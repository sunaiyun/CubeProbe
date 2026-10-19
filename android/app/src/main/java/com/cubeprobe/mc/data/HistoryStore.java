package com.cubeprobe.mc.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.cubeprobe.mc.net.ServerAddress;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small local history store. Nothing ever leaves the device. */
public final class HistoryStore {

    private static final String PREFS = "cubeprobe_history";
    private static final String KEY = "entries_v1";
    private static final int LIMIT = 8;

    private final SharedPreferences preferences;

    public HistoryStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Entry> load() {
        List<Entry> entries = new ArrayList<>();
        String raw = preferences.getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && entries.size() < LIMIT; i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String host = object.optString("host", "");
                int mode = object.optInt("mode", 0);
                int port = object.optInt("port", ServerAddress.defaultPort(mode));
                if (host.isEmpty() || port < 1 || port > 65535) {
                    continue;
                }
                entries.add(new Entry(host, port, object.optBoolean("explicit", false), mode,
                        object.optLong("seen", 0L), object.optLong("latency", 0L)));
            }
        } catch (Exception ignored) {
        }
        return entries;
    }

    public void add(Entry entry) {
        List<Entry> entries = load();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry existing = entries.get(i);
            if (existing.mode == entry.mode && existing.host.equalsIgnoreCase(entry.host)
                    && existing.port == entry.port) {
                entries.remove(i);
            }
        }
        entries.add(0, entry);
        while (entries.size() > LIMIT) {
            entries.remove(entries.size() - 1);
        }
        save(entries);
    }

    public void remove(Entry target) {
        List<Entry> entries = load();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.mode == target.mode && entry.host.equalsIgnoreCase(target.host)
                    && entry.port == target.port) {
                entries.remove(i);
            }
        }
        save(entries);
    }

    public void clear() {
        preferences.edit().remove(KEY).apply();
    }

    private void save(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try {
                JSONObject object = new JSONObject();
                object.put("host", entry.host);
                object.put("port", entry.port);
                object.put("explicit", entry.explicitPort);
                object.put("mode", entry.mode);
                object.put("seen", entry.lastSeen);
                object.put("latency", entry.latencyMs);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    public static final class Entry {
        public final String host;
        public final int port;
        public final boolean explicitPort;
        public final int mode;
        public final long lastSeen;
        public final long latencyMs;

        public Entry(String host, int port, boolean explicitPort, int mode,
                     long lastSeen, long latencyMs) {
            this.host = host;
            this.port = port;
            this.explicitPort = explicitPort;
            this.mode = mode;
            this.lastSeen = lastSeen;
            this.latencyMs = latencyMs;
        }

        public String inputValue() {
            String rendered = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
            return explicitPort ? rendered + ":" + port : rendered;
        }
    }
}
