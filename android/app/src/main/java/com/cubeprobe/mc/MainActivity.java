package com.cubeprobe.mc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cubeprobe.mc.data.HistoryStore;
import com.cubeprobe.mc.net.BedrockPinger;
import com.cubeprobe.mc.net.BedrockStatus;
import com.cubeprobe.mc.net.JavaPinger;
import com.cubeprobe.mc.net.JavaStatus;
import com.cubeprobe.mc.net.QueryException;
import com.cubeprobe.mc.net.ServerAddress;
import com.cubeprobe.mc.net.SrvResolver;
import com.cubeprobe.mc.ui.FaviconView;
import com.cubeprobe.mc.ui.ModeSwitch;
import com.cubeprobe.mc.ui.PulseDotView;
import com.cubeprobe.mc.ui.TagFlowLayout;
import com.cubeprobe.mc.util.MotdFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity implements ModeSwitch.OnModeChangeListener {

    private static final String TAG = "CubeProbe";
    private static final String STATE_PREFS = "cubeprobe_state";
    private static final String KEY_MODE = "mode";
    private static final String KEY_INPUT = "last_input";
    private static final int CONNECT_TIMEOUT_MS = 4200;
    private static final int MAX_PLAYER_CHIPS = 24;
    private static final int ACCENT_JAVA = 0xFF62F2A2;
    private static final int ACCENT_BEDROCK = 0xFF4EC8FF;

    private EditText addressInput;
    private TextView queryButton;
    private ProgressBar queryProgress;
    private TextView pasteButton;
    private TextView inputHint;
    private TextView clearButton;
    private ModeSwitch modeSwitch;
    private ScrollView scroll;
    private View content;
    private View topScrim;
    private LinearLayout loadingCard;
    private TextView loadingDetail;
    private LinearLayout errorCard;
    private TextView errorText;
    private View resultCard;
    private FaviconView serverIcon;
    private TextView serverHost;
    private TextView serverMeta;
    private LinearLayout statusChip;
    private PulseDotView statusDot;
    private TextView statusText;
    private TextView motd;
    private TextView onlineValue;
    private TextView maxValue;
    private TextView latencyValue;
    private TextView versionValue;
    private ProgressBar capacityBar;
    private TextView capacityText;
    private TagFlowLayout detailTags;
    private LinearLayout playersSection;
    private TextView playerCountLabel;
    private TagFlowLayout playerTags;
    private TextView requeryButton;
    private TextView copyButton;
    private ImageButton shareButton;
    private LinearLayout recentContainer;
    private LinearLayout emptyHistory;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Future<?> activeQuery;
    private long queryGeneration;
    private boolean queryRunning;
    private int mode = ModeSwitch.MODE_JAVA;

    private HistoryStore historyStore;
    private SharedPreferences statePrefs;

    private ServerAddress lastAddress;
    private int lastMode = -1;
    private int lastConnectPort;
    private String lastConnectHost;
    private boolean lastViaSrv;
    private String lastMotdPlain;
    private String lastVersion;
    private int lastOnline;
    private int lastMax;
    private int lastProtocol;
    private long lastLatency;
    private String lastGameMode;
    private String lastMap;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(R.layout.activity_main);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        bindViews();
        configureInsets();

        statePrefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        historyStore = new HistoryStore(this);

        int restoredMode = statePrefs.getInt(KEY_MODE, ModeSwitch.MODE_JAVA);
        applyMode(restoredMode, false);
        String savedInput = statePrefs.getString(KEY_INPUT, "");
        if (!TextUtils.isEmpty(savedInput)) {
            addressInput.setText(savedInput);
            addressInput.setSelection(addressInput.length());
        }

        View focusCatcher = findViewById(R.id.content);
        focusCatcher.setFocusableInTouchMode(true);
        focusCatcher.requestFocus();
        addressInput.clearFocus();

        renderHistory();
        setBusy(false);
        runEntranceAnimation();
    }

    @SuppressWarnings("deprecation")
    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30Impl.configureWindow(window, decorView);
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void configureInsets() {
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int[] bars = Api30Impl.systemBars(insets);
                top = bars[0];
                bottom = bars[1];
                left = bars[2];
                right = bars[3];
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            if (topScrim != null) {
                ViewGroup.LayoutParams params = topScrim.getLayoutParams();
                if (params.height != top) {
                    params.height = top;
                    topScrim.setLayoutParams(params);
                }
            }
            return insets;
        });
        scroll.requestApplyInsets();
    }

    private void bindViews() {
        scroll = findViewById(R.id.scroll);
        content = findViewById(R.id.content);
        topScrim = findViewById(R.id.topScrim);
        modeSwitch = findViewById(R.id.modeSwitch);
        addressInput = findViewById(R.id.addressInput);
        inputHint = findViewById(R.id.inputHint);
        pasteButton = findViewById(R.id.pasteButton);
        queryButton = findViewById(R.id.queryButton);
        queryProgress = findViewById(R.id.queryProgress);
        loadingCard = findViewById(R.id.loadingCard);
        loadingDetail = findViewById(R.id.loadingDetail);
        errorCard = findViewById(R.id.errorCard);
        errorText = findViewById(R.id.errorText);
        resultCard = findViewById(R.id.resultCard);
        clearButton = findViewById(R.id.clearButton);
        recentContainer = findViewById(R.id.recentContainer);
        emptyHistory = findViewById(R.id.emptyHistory);

        serverIcon = findViewById(R.id.serverIcon);
        serverHost = findViewById(R.id.serverHost);
        serverMeta = findViewById(R.id.serverMeta);
        statusChip = findViewById(R.id.statusChip);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        motd = findViewById(R.id.motd);
        onlineValue = findViewById(R.id.onlineValue);
        maxValue = findViewById(R.id.maxValue);
        latencyValue = findViewById(R.id.latencyValue);
        versionValue = findViewById(R.id.versionValue);
        capacityBar = findViewById(R.id.capacityBar);
        capacityText = findViewById(R.id.capacityText);
        detailTags = findViewById(R.id.detailTags);
        playersSection = findViewById(R.id.playersSection);
        playerCountLabel = findViewById(R.id.playerCountLabel);
        playerTags = findViewById(R.id.playerTags);
        requeryButton = findViewById(R.id.requeryButton);
        copyButton = findViewById(R.id.copyButton);
        shareButton = findViewById(R.id.shareButton);

        serverIcon.setClipToOutline(true);
        modeSwitch.setOnModeChangeListener(this);
        queryButton.setOnClickListener(v -> startQuery());
        pasteButton.setOnClickListener(v -> pasteAddress());
        requeryButton.setOnClickListener(v -> startQuery());
        copyButton.setOnClickListener(v -> copyStatus());
        shareButton.setOnClickListener(v -> shareStatus());
        clearButton.setOnClickListener(v -> confirmClearHistory());

        addressInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                startQuery();
                return true;
            }
            return false;
        });

        setTouchFeedback(queryButton);
        setTouchFeedback(pasteButton);
        setTouchFeedback(requeryButton);
        setTouchFeedback(copyButton);
        setTouchFeedback(clearButton);
    }

    private void setTouchFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
    }

    private void runEntranceAnimation() {
        View header = findViewById(R.id.header);
        View hero = findViewById(R.id.hero);
        header.setAlpha(0f);
        header.setTranslationY(dp(10));
        hero.setAlpha(0f);
        hero.setTranslationY(dp(16));
        header.animate().alpha(1f).translationY(0f)
                .setDuration(360).setStartDelay(40)
                .setInterpolator(new DecelerateInterpolator()).start();
        hero.animate().alpha(1f).translationY(0f)
                .setDuration(460).setStartDelay(120)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    public void onModeChanged(int newMode) {
        applyMode(newMode, true);
    }

    private void applyMode(int newMode, boolean clearPrevious) {
        if (newMode != ModeSwitch.MODE_JAVA && newMode != ModeSwitch.MODE_BEDROCK) {
            return;
        }
        mode = newMode;
        modeSwitch.setMode(newMode, false);
        boolean bedrock = newMode == ModeSwitch.MODE_BEDROCK;
        addressInput.setHint(bedrock ? R.string.address_hint_bedrock : R.string.address_hint_java);
        inputHint.setText(bedrock ? R.string.input_hint_bedrock : R.string.input_hint_java);
        statePrefs.edit().putInt(KEY_MODE, newMode).apply();
        if (clearPrevious) {
            hideResult();
            hideError();
            lastAddress = null;
        }
    }

    private void startQuery() {
        String raw = addressInput.getText().toString();
        ServerAddress address;
        try {
            address = ServerAddress.parse(raw, mode);
        } catch (QueryException e) {
            boolean empty = raw.trim().isEmpty();
            showLocalError(empty ? getString(R.string.error_empty) : messageFor(e, mode));
            addressInput.requestFocus();
            if (empty) {
                showKeyboard();
            }
            return;
        }

        if (!hasNetwork()) {
            showLocalError(getString(R.string.error_network));
            return;
        }

        if (queryRunning) {
            cancelActiveQuery();
        }

        long generation = ++queryGeneration;
        queryRunning = true;
        activeQuery = null;
        lastAddress = null;
        lastMode = -1;
        saveState();
        hideKeyboard();
        setBusy(true);
        hideError();
        hideResult();
        showLoading(address);

        final int queryMode = mode;
        final ServerAddress target = address;
        activeQuery = executor.submit(() -> {
            if (queryMode == ModeSwitch.MODE_JAVA) {
                runJavaQuery(generation, target);
            } else {
                runBedrockQuery(generation, target);
            }
        });
    }

    private void cancelActiveQuery() {
        if (activeQuery != null) {
            activeQuery.cancel(true);
            activeQuery = null;
        }
    }

    private boolean hasNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.getActiveNetwork() != null;
    }

    private void runJavaQuery(long generation, ServerAddress address) {
        String targetHost = address.host;
        int targetPort = address.port;
        boolean viaSrv = false;
        try {
            if (!address.explicitPort && !address.isIpLiteral()) {
                updateLoadingDetail(generation, getString(R.string.loading_srv)
                        + "  ·  " + address.host);
                SrvResolver.Record record = SrvResolver.resolve(address.host);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (record != null) {
                    targetHost = record.target;
                    targetPort = record.port;
                    viaSrv = true;
                }
            }
            updateLoadingDetail(generation, getString(R.string.loading_java)
                    + "  ·  " + targetHost + ":" + targetPort);
            JavaStatus status = JavaPinger.query(address, targetHost, targetPort, CONNECT_TIMEOUT_MS);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            final String connectedHost = targetHost;
            final int connectedPort = targetPort;
            final boolean usedSrv = viaSrv;
            runOnUiThread(() -> {
                if (generation == queryGeneration) {
                    showJavaResult(address, status, connectedHost, connectedPort, usedSrv);
                }
            });
        } catch (QueryException e) {
            postQueryError(generation, e, ModeSwitch.MODE_JAVA);
        } catch (Exception e) {
            postQueryError(generation, QueryException.wrap(e), ModeSwitch.MODE_JAVA);
        }
    }

    private void runBedrockQuery(long generation, ServerAddress address) {
        try {
            updateLoadingDetail(generation, getString(R.string.loading_bedrock)
                    + "  ·  " + address.display());
            BedrockStatus status = BedrockPinger.query(address, CONNECT_TIMEOUT_MS);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            runOnUiThread(() -> {
                if (generation == queryGeneration) {
                    showBedrockResult(address, status);
                }
            });
        } catch (QueryException e) {
            postQueryError(generation, e, ModeSwitch.MODE_BEDROCK);
        } catch (Exception e) {
            postQueryError(generation, QueryException.wrap(e), ModeSwitch.MODE_BEDROCK);
        }
    }

    private void updateLoadingDetail(long generation, String detail) {
        runOnUiThread(() -> {
            if (generation == queryGeneration && queryRunning) {
                loadingDetail.setText(detail);
            }
        });
    }

    private void postQueryError(long generation, QueryException error, int queryMode) {
        Log.w(TAG, "query failed: " + error.getKind() + " / " + error.getMessage());
        runOnUiThread(() -> {
            if (generation == queryGeneration) {
                showQueryError(error, queryMode);
            }
        });
    }

    private void showJavaResult(ServerAddress address, JavaStatus status,
                                String connectHost, int connectPort, boolean viaSrv) {
        if (isFinishing()) {
            return;
        }
        finishQueryUi();
        applyResultAccent(ModeSwitch.MODE_JAVA);
        serverHost.setText(address.host);
        String meta = getString(R.string.result_java_edition) + "  ·  " + address.display();
        if (viaSrv) {
            meta += "  ·  SRV";
        }
        serverMeta.setText(meta);

        CharSequence formatted = MotdFormatter.formatJava(status.description);
        String plain = formatted.toString().trim();
        if (plain.isEmpty()) {
            formatted = "A Minecraft Server";
            plain = "A Minecraft Server";
        }
        motd.setText(formatted);
        lastMotdPlain = plain;

        onlineValue.setText(formatCount(status.online));
        maxValue.setText(status.max > 0 ? "/ " + formatCount(status.max) : "/ —");
        latencyValue.setText(getString(R.string.latency_ms, (int) status.latencyMs));
        versionValue.setText(status.versionName);
        bindCapacity(status.online, status.max);
        bindIcon(status.faviconBase64, false);
        bindJavaDetails(status, viaSrv);
        bindPlayers(status);
        statusText.setText(R.string.status_online);
        statusDot.setPulsing(true);
        statusChip.setContentDescription(getString(R.string.status_online));

        lastMode = ModeSwitch.MODE_JAVA;
        lastAddress = address;
        lastConnectHost = connectHost;
        lastConnectPort = connectPort;
        lastViaSrv = viaSrv;
        lastVersion = status.versionName;
        lastOnline = status.online;
        lastMax = status.max;
        lastProtocol = status.protocol;
        lastLatency = status.latencyMs;
        lastGameMode = "";
        lastMap = "";

        revealResult();
        historyStore.add(new HistoryStore.Entry(address.host, address.port, address.explicitPort,
                ModeSwitch.MODE_JAVA, System.currentTimeMillis(), status.latencyMs));
        renderHistory();
        saveState();
        scrollToResult();
    }

    private void showBedrockResult(ServerAddress address, BedrockStatus status) {
        if (isFinishing()) {
            return;
        }
        finishQueryUi();
        applyResultAccent(ModeSwitch.MODE_BEDROCK);
        serverHost.setText(address.host);
        serverMeta.setText(getString(R.string.result_bedrock_edition) + "  ·  " + address.display());

        String extra = status.subMotd == null ? "" : status.subMotd.trim();
        String rawMotd = status.motd;
        if (!extra.isEmpty()) {
            rawMotd = rawMotd + "\n" + extra;
        }
        CharSequence formatted = MotdFormatter.formatLegacy(rawMotd);
        String plain = formatted.toString().trim();
        if (plain.isEmpty()) {
            formatted = "Minecraft Bedrock Server";
            plain = "Minecraft Bedrock Server";
        }
        motd.setText(formatted);
        lastMotdPlain = plain;

        onlineValue.setText(formatCount(status.online));
        maxValue.setText(status.max > 0 ? "/ " + formatCount(status.max) : "/ —");
        latencyValue.setText(getString(R.string.latency_ms, (int) status.latencyMs));
        versionValue.setText(status.versionName);
        bindCapacity(status.online, status.max);
        bindIcon(null, true);
        bindBedrockDetails(status);
        playersSection.setVisibility(View.GONE);
        playerTags.removeAllViews();
        statusText.setText(R.string.status_online);
        statusDot.setPulsing(true);
        statusChip.setContentDescription(getString(R.string.status_online));

        lastMode = ModeSwitch.MODE_BEDROCK;
        lastAddress = address;
        lastConnectHost = address.host;
        lastConnectPort = address.port;
        lastViaSrv = false;
        lastVersion = status.versionName;
        lastOnline = status.online;
        lastMax = status.max;
        lastProtocol = status.protocol;
        lastLatency = status.latencyMs;
        lastGameMode = prettyGameMode(status.gameMode);
        lastMap = extra;

        revealResult();
        historyStore.add(new HistoryStore.Entry(address.host, address.port, address.explicitPort,
                ModeSwitch.MODE_BEDROCK, System.currentTimeMillis(), status.latencyMs));
        renderHistory();
        saveState();
        scrollToResult();
    }

    private void showQueryError(QueryException error, int queryMode) {
        if (isFinishing()) {
            return;
        }
        finishQueryUi();
        hideResult();
        lastAddress = null;
        lastMode = -1;
        showErrorCard(messageFor(error, queryMode));
        scrollToError();
    }

    private void revealResult() {
        errorCard.setVisibility(View.GONE);
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(dp(18));
        resultCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start();
    }

    private void finishQueryUi() {
        queryRunning = false;
        setBusy(false);
        hideLoading();
    }

    private void setBusy(boolean busy) {
        queryButton.setEnabled(!busy);
        queryButton.setText(busy ? "" : getString(R.string.query));
        queryButton.setContentDescription(busy ? getString(R.string.querying) : getString(R.string.query));
        queryProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        modeSwitch.setEnabled(!busy);
        modeSwitch.setAlpha(busy ? 0.55f : 1f);
        addressInput.setEnabled(!busy);
        addressInput.setAlpha(busy ? 0.72f : 1f);
        pasteButton.setEnabled(!busy);
        pasteButton.setAlpha(busy ? 0.45f : 1f);
    }

    private void showLoading(ServerAddress address) {
        loadingCard.animate().cancel();
        loadingCard.setVisibility(View.VISIBLE);
        loadingCard.setAlpha(0f);
        loadingCard.setTranslationY(dp(8));
        loadingCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        boolean bedrock = mode == ModeSwitch.MODE_BEDROCK;
        loadingDetail.setText(getString(bedrock ? R.string.loading_bedrock : R.string.loading_java)
                + "  ·  " + address.display());
    }

    private void hideLoading() {
        loadingCard.animate().cancel();
        loadingCard.setVisibility(View.GONE);
        loadingCard.setAlpha(1f);
    }

    private void showLocalError(String message) {
        queryRunning = false;
        setBusy(false);
        hideLoading();
        hideResult();
        lastAddress = null;
        lastMode = -1;
        showErrorCard(message);
    }

    private void showErrorCard(String message) {
        errorText.setText(message);
        errorCard.setVisibility(View.VISIBLE);
        errorCard.setAlpha(0f);
        errorCard.setTranslationY(dp(8));
        errorCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hideError() {
        errorCard.animate().cancel();
        errorCard.setVisibility(View.GONE);
        errorCard.setAlpha(1f);
    }

    private void hideResult() {
        resultCard.animate().cancel();
        resultCard.setVisibility(View.GONE);
        resultCard.setAlpha(1f);
        resultCard.setTranslationY(0f);
    }

    private void scrollToResult() {
        scroll.postDelayed(() -> {
            if (resultCard.getVisibility() == View.VISIBLE
                    && resultCard.getHeight() > 0
                    && !isFinishing()) {
                int offset = dp(12);
                scroll.smoothScrollTo(0, Math.max(0, resultCard.getTop() - offset));
            }
        }, 260);
    }

    private void scrollToError() {
        scroll.postDelayed(() -> {
            if (errorCard.getVisibility() == View.VISIBLE && errorCard.getHeight() > 0) {
                scroll.smoothScrollTo(0, Math.max(0, errorCard.getTop() - dp(16)));
            }
        }, 220);
    }

    private void bindCapacity(int online, int max) {
        int percent = 0;
        if (max > 0) {
            percent = Math.min(100, Math.round(online * 100f / max));
        }
        capacityBar.setProgress(percent);
        if (max > 0) {
            capacityText.setText(formatCount(online) + " / " + formatCount(max)
                    + "  ·  " + percent + "%");
        } else {
            capacityText.setText(R.string.result_undisclosed);
        }
    }

    private void applyResultAccent(int resultMode) {
        boolean bedrock = resultMode == ModeSwitch.MODE_BEDROCK;
        int accent = bedrock ? ACCENT_BEDROCK : ACCENT_JAVA;
        statusChip.setBackgroundResource(bedrock
                ? R.drawable.bg_status_bedrock : R.drawable.bg_status_online);
        statusText.setTextColor(accent);
        statusDot.setColor(accent);
        onlineValue.setTextColor(accent);
        capacityBar.setProgressDrawable(getDrawable(bedrock
                ? R.drawable.capacity_progress_bedrock : R.drawable.capacity_progress));
        serverIcon.setBackgroundResource(bedrock
                ? R.drawable.bg_favicon_bedrock : R.drawable.bg_favicon_java);
    }

    private void bindIcon(String faviconBase64, boolean bedrock) {
        boolean applied = false;
        if (!TextUtils.isEmpty(faviconBase64)) {
            try {
                String clean = faviconBase64.replaceAll("\\s", "");
                byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    int sample = 1;
                    while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 192) {
                        sample *= 2;
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (bitmap != null) {
                        serverIcon.setImageTintList(null);
                        serverIcon.setPadding(0, 0, 0, 0);
                        serverIcon.setScaleType(ImageView.ScaleType.FIT_XY);
                        serverIcon.setImageBitmap(bitmap);
                        applied = true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "could not decode favicon", e);
            }
        }
        if (!applied) {
            int accent = bedrock ? ACCENT_BEDROCK : ACCENT_JAVA;
            serverIcon.setImageTintList(ColorStateList.valueOf(accent));
            serverIcon.setPadding(dp(11), dp(11), dp(11), dp(11));
            serverIcon.setScaleType(ImageView.ScaleType.CENTER);
            serverIcon.setImageResource(bedrock ? R.drawable.ic_bedrock_cube : R.drawable.ic_cube);
        }
    }

    private void bindJavaDetails(JavaStatus status, boolean viaSrv) {
        detailTags.removeAllViews();
        addDetailTag(getString(R.string.result_java_edition), ACCENT_JAVA);
        if (status.protocol > 0) {
            addDetailTag(getString(R.string.result_protocol, status.protocol), getColor(R.color.text_secondary));
        }
        String software = prettySoftware(status.software);
        if (!software.isEmpty()) {
            addDetailTag(software, getColor(R.color.text_secondary));
        }
        if (status.secureChat) {
            addDetailTag(getString(R.string.result_secure_chat), getColor(R.color.text_secondary));
        }
        if (status.legacy) {
            addDetailTag("Legacy Ping", getColor(R.color.text_secondary));
        }
        if (viaSrv) {
            addDetailTag("SRV", getColor(R.color.text_secondary));
        }
    }

    private void bindBedrockDetails(BedrockStatus status) {
        detailTags.removeAllViews();
        addDetailTag(getString(R.string.result_bedrock_edition), ACCENT_BEDROCK);
        if (status.protocol > 0) {
            addDetailTag(getString(R.string.result_protocol, status.protocol), getColor(R.color.text_secondary));
        }
        if (!status.gameMode.trim().isEmpty()) {
            addDetailTag(prettyGameMode(status.gameMode), getColor(R.color.text_secondary));
        }
        addDetailTag("UDP", getColor(R.color.text_secondary));
    }

    private String prettyGameMode(String value) {
        if (value == null) {
            return "";
        }
        String mode = value.trim();
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "survival":
                return "生存";
            case "creative":
                return "创造";
            case "adventure":
                return "冒险";
            case "spectator":
                return "旁观";
            default:
                return mode;
        }
    }

    private String prettySoftware(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if ("FML".equalsIgnoreCase(normalized) || "forge".equalsIgnoreCase(normalized)) {
            return "Forge";
        }
        if (normalized.toLowerCase(Locale.ROOT).contains("fabric")) {
            return "Fabric";
        }
        if (normalized.toLowerCase(Locale.ROOT).contains("quilt")) {
            return "Quilt";
        }
        if ("modded".equalsIgnoreCase(normalized)) {
            return "Modded";
        }
        return normalized;
    }

    private void bindPlayers(JavaStatus status) {
        playerTags.removeAllViews();
        if (status.sample == null || status.sample.isEmpty()) {
            playersSection.setVisibility(View.GONE);
            return;
        }
        playersSection.setVisibility(View.VISIBLE);
        playerCountLabel.setText(getString(R.string.players_count, status.online));
        int shown = Math.min(status.sample.size(), MAX_PLAYER_CHIPS);
        for (int i = 0; i < shown; i++) {
            String name = status.sample.get(i);
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            addPlayerTag(MotdFormatter.formatLegacy(name));
        }
        int remaining = status.sample.size() - shown;
        if (remaining > 0) {
            addPlayerTag("+" + remaining);
        }
    }

    private void addDetailTag(String text, int textColor) {
        TextView tag = createTag(text, textColor, 10.5f);
        detailTags.addView(tag);
    }

    private void addPlayerTag(CharSequence text) {
        TextView tag = createTag(text, getColor(R.color.text_primary), 10.5f);
        tag.setMaxWidth(dp(230));
        playerTags.addView(tag);
    }

    private TextView createTag(CharSequence text, int textColor, float sizeSp) {
        TextView tag = new TextView(this);
        tag.setText(text);
        tag.setTextSize(sizeSp);
        tag.setTextColor(textColor);
        tag.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tag.setIncludeFontPadding(false);
        tag.setGravity(Gravity.CENTER);
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        tag.setBackgroundResource(R.drawable.bg_chip);
        tag.setPadding(dp(10), 0, dp(10), 0);
        tag.setMinHeight(dp(27));
        tag.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)));
        return tag;
    }

    private void renderHistory() {
        List<HistoryStore.Entry> entries = historyStore.load();
        recentContainer.removeAllViews();
        emptyHistory.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        clearButton.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        for (HistoryStore.Entry entry : entries) {
            View row = getLayoutInflater().inflate(R.layout.item_recent, recentContainer, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
            params.bottomMargin = dp(8);
            row.setLayoutParams(params);

            TextView badge = row.findViewById(R.id.recentMode);
            TextView host = row.findViewById(R.id.recentHost);
            TextView meta = row.findViewById(R.id.recentMeta);
            boolean bedrock = entry.mode == ModeSwitch.MODE_BEDROCK;
            if (bedrock) {
                badge.setText("B");
                badge.setTextColor(ACCENT_BEDROCK);
                badge.setBackgroundResource(R.drawable.bg_mode_badge_bedrock);
            } else {
                badge.setText("J");
                badge.setTextColor(ACCENT_JAVA);
                badge.setBackgroundResource(R.drawable.bg_mode_badge_java);
            }
            host.setText(entry.host);
            List<String> pieces = new ArrayList<>();
            pieces.add(bedrock ? "Bedrock" : "Java");
            pieces.add(String.valueOf(entry.port));
            if (entry.latencyMs > 0) {
                pieces.add(entry.latencyMs + " ms");
            }
            pieces.add(relativeTime(entry.lastSeen));
            meta.setText(TextUtils.join("  ·  ", pieces));
            row.setContentDescription(entry.host + "，" + (bedrock ? "基岩版" : "Java 版")
                    + "，点击重新查询，长按移除");
            row.setOnClickListener(v -> {
                applyMode(entry.mode, true);
                addressInput.setText(entry.inputValue());
                addressInput.setSelection(addressInput.length());
                startQuery();
            });
            row.setOnLongClickListener(v -> {
                historyStore.remove(entry);
                renderHistory();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                Toast.makeText(this, R.string.toast_removed, Toast.LENGTH_SHORT).show();
                return true;
            });
            recentContainer.addView(row);
        }
    }

    private String relativeTime(long timestamp) {
        if (timestamp <= 0) {
            return "刚刚";
        }
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0 || diff < 60_000L) {
            return "刚刚";
        }
        long minutes = diff / 60_000L;
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        long hours = diff / 3_600_000L;
        if (hours < 24) {
            return hours + " 小时前";
        }
        long days = diff / 86_400_000L;
        if (days < 7) {
            return days + " 天前";
        }
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(timestamp));
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_clear, (dialog, which) -> {
                    historyStore.clear();
                    renderHistory();
                })
                .show();
    }

    private void pasteAddress() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, R.string.toast_paste_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, R.string.toast_paste_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.toast_paste_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String value = text.toString().trim();
        addressInput.setText(value);
        addressInput.setSelection(addressInput.length());
        hideError();
        statePrefs.edit().putString(KEY_INPUT, value).apply();
    }

    private void copyStatus() {
        String text = buildShareText();
        if (text == null) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.share_subject), text));
        Toast.makeText(this, R.string.toast_copy_done, Toast.LENGTH_SHORT).show();
    }

    private void shareStatus() {
        String text = buildShareText();
        if (text == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
        } catch (Exception ignored) {
        }
    }

    private String buildShareText() {
        if (lastAddress == null || lastMode < 0) {
            return null;
        }
        boolean bedrock = lastMode == ModeSwitch.MODE_BEDROCK;
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.copy_header)).append('\n');
        text.append(bedrock ? "Bedrock Edition" : "Java Edition")
                .append("  ·  ")
                .append(lastAddress.host)
                .append(':')
                .append(lastConnectPort)
                .append('\n');
        if (lastViaSrv) {
            text.append("SRV → ").append(lastConnectHost).append(':')
                    .append(lastConnectPort).append('\n');
        }
        text.append('\n').append(lastMotdPlain).append('\n');
        text.append('\n').append(getString(R.string.result_players)).append("：")
                .append(lastOnline).append(lastMax > 0 ? " / " + lastMax : "")
                .append('\n');
        text.append(getString(R.string.result_latency)).append("：")
                .append(lastLatency).append(" ms\n");
        text.append(getString(R.string.result_version)).append("：")
                .append(lastVersion).append('\n');
        if (lastProtocol > 0) {
            text.append(getString(R.string.result_protocol, lastProtocol)).append('\n');
        }
        if (bedrock) {
            if (!TextUtils.isEmpty(lastGameMode)) {
                text.append(getString(R.string.result_gamemode)).append("：")
                        .append(lastGameMode).append('\n');
            }
            if (!TextUtils.isEmpty(lastMap)) {
                text.append(getString(R.string.result_map)).append("：")
                        .append(lastMap).append('\n');
            }
        }
        text.append('\n').append(getString(R.string.footer_line));
        return text.toString();
    }

    private String formatCount(int value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return trimDecimal(value / 1000.0) + "k";
        }
        return trimDecimal(value / 1_000_000.0) + "M";
    }

    private String trimDecimal(double value) {
        String rendered = String.format(Locale.US, "%.1f", value);
        if (rendered.endsWith(".0")) {
            rendered = rendered.substring(0, rendered.length() - 2);
        }
        return rendered;
    }

    private String messageFor(QueryException error, int queryMode) {
        if (error == null) {
            return getString(R.string.error_protocol);
        }
        switch (error.getKind()) {
            case UNKNOWN_HOST:
                return getString(R.string.error_host);
            case TIMEOUT:
                return getString(queryMode == ModeSwitch.MODE_BEDROCK
                        ? R.string.error_bedrock_timeout : R.string.error_timeout);
            case REFUSED:
                return getString(R.string.error_refused);
            case NETWORK:
                return getString(R.string.error_network);
            case PROTOCOL:
                return getString(R.string.error_protocol);
            case INVALID:
                String message = error.getMessage();
                if (message != null && message.contains("port")) {
                    return getString(R.string.error_invalid_port);
                }
                return getString(R.string.error_invalid_host);
            default:
                String raw = error.getMessage();
                if (TextUtils.isEmpty(raw)) {
                    return getString(R.string.error_protocol);
                }
                if (raw.length() > 150) {
                    raw = raw.substring(0, 150);
                }
                return getString(R.string.error_generic, raw);
        }
    }

    private void showKeyboard() {
        addressInput.postDelayed(() -> {
            addressInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(addressInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 120);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(addressInput.getWindowToken(), 0);
        }
        addressInput.clearFocus();
    }

    private void saveState() {
        statePrefs.edit()
                .putInt(KEY_MODE, mode)
                .putString(KEY_INPUT, addressInput.getText().toString().trim())
                .apply();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        saveState();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        queryGeneration++;
        cancelActiveQuery();
        executor.shutdownNow();
        super.onDestroy();
    }

    /**
     * API 30 types live in their own class so Android 7/8/9 never has to load
     * WindowInsetsController or android.graphics.Insets while verifying MainActivity.
     */
    private static final class Api30Impl {
        @android.annotation.TargetApi(Build.VERSION_CODES.R)
        static void configureWindow(Window window, View decorView) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }

        @android.annotation.TargetApi(Build.VERSION_CODES.R)
        static int[] systemBars(WindowInsets insets) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            return new int[]{bars.top, bars.bottom, bars.left, bars.right};
        }
    }
}
