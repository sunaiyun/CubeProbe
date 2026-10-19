package com.cubeprobe.mc.util;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the JSON chat component / legacy MOTD pair into a styled CharSequence.
 * Named Minecraft colours are tuned slightly brighter for the app's dark surface.
 */
public final class MotdFormatter {

    private static final int MAX_LENGTH = 2000;

    private static final int[] LEGACY_COLORS = {
            0xFF9AA7B4, // 0 black
            0xFF7C8CFF, // 1 dark blue
            0xFF4ADE80, // 2 dark green
            0xFF2DD4BF, // 3 dark aqua
            0xFFFB7185, // 4 dark red
            0xFFC084FC, // 5 dark purple
            0xFFFBBF24, // 6 gold
            0xFFA3B0BF, // 7 gray
            0xFF7C8C99, // 8 dark gray
            0xFF60A5FA, // 9 blue
            0xFF4ADE80, // a green
            0xFF67E8F9, // b aqua
            0xFFF87171, // c red
            0xFFF0ABFC, // d light purple
            0xFFFDE047, // e yellow
            0xFFF8FAFC  // f white
    };

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();

    static {
        NAMED_COLORS.put("black", LEGACY_COLORS[0]);
        NAMED_COLORS.put("dark_blue", LEGACY_COLORS[1]);
        NAMED_COLORS.put("dark_green", LEGACY_COLORS[2]);
        NAMED_COLORS.put("dark_aqua", LEGACY_COLORS[3]);
        NAMED_COLORS.put("dark_red", LEGACY_COLORS[4]);
        NAMED_COLORS.put("dark_purple", LEGACY_COLORS[5]);
        NAMED_COLORS.put("gold", LEGACY_COLORS[6]);
        NAMED_COLORS.put("gray", LEGACY_COLORS[7]);
        NAMED_COLORS.put("dark_gray", LEGACY_COLORS[8]);
        NAMED_COLORS.put("blue", LEGACY_COLORS[9]);
        NAMED_COLORS.put("green", LEGACY_COLORS[10]);
        NAMED_COLORS.put("aqua", LEGACY_COLORS[11]);
        NAMED_COLORS.put("red", LEGACY_COLORS[12]);
        NAMED_COLORS.put("light_purple", LEGACY_COLORS[13]);
        NAMED_COLORS.put("yellow", LEGACY_COLORS[14]);
        NAMED_COLORS.put("white", LEGACY_COLORS[15]);
    }

    private MotdFormatter() {
    }

    public static CharSequence formatJava(Object description) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendJson(builder, description, new Style(), 0);
        if (builder.length() > MAX_LENGTH) {
            builder.delete(MAX_LENGTH, builder.length());
        }
        return builder;
    }

    public static CharSequence formatLegacy(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendStyledText(builder, text == null ? "" : text, new Style());
        if (builder.length() > MAX_LENGTH) {
            builder.delete(MAX_LENGTH, builder.length());
        }
        return builder;
    }

    public static String plainJava(Object description) {
        return formatJava(description).toString().trim();
    }

    public static String plainLegacy(String text) {
        return formatLegacy(text).toString().trim();
    }

    private static void appendJson(SpannableStringBuilder out, Object node, Style inherited, int depth) {
        if (node == null || node == JSONObject.NULL || depth > 16 || out.length() >= MAX_LENGTH) {
            return;
        }
        if (node instanceof String) {
            appendStyledText(out, (String) node, inherited);
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                appendJson(out, array.opt(i), inherited, depth + 1);
            }
            return;
        }
        if (!(node instanceof JSONObject)) {
            appendStyledText(out, String.valueOf(node), inherited);
            return;
        }

        JSONObject object = (JSONObject) node;
        Style style = inherited.copy();
        if (object.has("color")) {
            String color = object.optString("color", "");
            if ("reset".equalsIgnoreCase(color) || color.isEmpty()) {
                style.color = 0;
            } else {
                int parsed = parseColor(color);
                if (parsed != 0) {
                    style.color = parsed;
                }
            }
        }
        if (object.has("bold")) {
            style.bold = object.optBoolean("bold", false);
        }
        if (object.has("italic")) {
            style.italic = object.optBoolean("italic", false);
        }
        if (object.has("underlined")) {
            style.underline = object.optBoolean("underlined", false);
        }
        if (object.has("strikethrough")) {
            style.strike = object.optBoolean("strikethrough", false);
        }

        String text = object.optString("text", null);
        if (text != null && !text.isEmpty()) {
            appendStyledText(out, text, style);
        }

        JSONArray extra = object.optJSONArray("extra");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                appendJson(out, extra.opt(i), style, depth + 1);
            }
        }
    }

    private static void appendStyledText(SpannableStringBuilder out, String text, Style base) {
        if (text == null || text.isEmpty() || out.length() >= MAX_LENGTH) {
            return;
        }
        Style current = base.copy();
        int index = 0;
        while (index < text.length() && out.length() < MAX_LENGTH) {
            char c = text.charAt(index);
            if (c == '\u00A7' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'x' && index + 13 < text.length()) {
                    int parsed = parseHexSequence(text, index + 2);
                    if (parsed != 0) {
                        current.color = parsed;
                        index += 14;
                        continue;
                    }
                }
                if (applyCode(current, base, code)) {
                    index += 2;
                    continue;
                }
                index++;
                continue;
            }

            int next = text.indexOf('\u00A7', index);
            if (next < 0) {
                next = text.length();
            }
            int end = Math.min(next, index + (MAX_LENGTH - out.length()));
            if (end > index) {
                int spanStart = out.length();
                out.append(text, index, end);
                applySpans(out, spanStart, out.length(), current);
            }
            index = next;
        }
    }

    private static int parseHexSequence(String text, int start) {
        if (start + 12 > text.length()) {
            return 0;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int pos = start + i * 2;
            if (text.charAt(pos) != '\u00A7') {
                return 0;
            }
            char digit = text.charAt(pos + 1);
            if (Character.digit(digit, 16) < 0) {
                return 0;
            }
            hex.append(digit);
        }
        try {
            return Color.parseColor("#" + hex);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean applyCode(Style current, Style base, char code) {
        if (code >= '0' && code <= '9') {
            current.color = LEGACY_COLORS[code - '0'];
            return true;
        }
        if (code >= 'a' && code <= 'f') {
            current.color = LEGACY_COLORS[10 + (code - 'a')];
            return true;
        }
        switch (code) {
            case 'k':
                return true;
            case 'l':
                current.bold = true;
                return true;
            case 'm':
                current.strike = true;
                return true;
            case 'n':
                current.underline = true;
                return true;
            case 'o':
                current.italic = true;
                return true;
            case 'r':
                current.set(base);
                return true;
            default:
                return false;
        }
    }

    private static void applySpans(SpannableStringBuilder out, int start, int end, Style style) {
        if (end <= start) {
            return;
        }
        if (style.color != 0) {
            out.setSpan(new ForegroundColorSpan(style.color), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.bold && style.italic) {
            out.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (style.bold) {
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (style.italic) {
            out.setSpan(new StyleSpan(Typeface.ITALIC), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.underline) {
            out.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.strike) {
            out.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int parseColor(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(lower);
        if (named != null) {
            return named;
        }
        if (lower.startsWith("#")) {
            try {
                String hex = lower.substring(1);
                if (hex.length() == 3) {
                    hex = "" + hex.charAt(0) + hex.charAt(0)
                            + hex.charAt(1) + hex.charAt(1)
                            + hex.charAt(2) + hex.charAt(2);
                }
                if (hex.length() == 6) {
                    return Color.parseColor("#" + hex);
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static class Style {
        int color;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;

        Style copy() {
            Style copy = new Style();
            copy.color = color;
            copy.bold = bold;
            copy.italic = italic;
            copy.underline = underline;
            copy.strike = strike;
            return copy;
        }

        void set(Style source) {
            color = source.color;
            bold = source.bold;
            italic = source.italic;
            underline = source.underline;
            strike = source.strike;
        }
    }
}
