package com.cubeprobe.mc.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Resolves Java Edition SRV records through DNS-over-HTTPS.
 *
 * Android does not expose a stable SRV resolver in its public API, so we use
 * Cloudflare/Google DoH. Failure is non-fatal: the caller falls back to port 25565.
 */
public final class SrvResolver {

    public static final class Record {
        public final String target;
        public final int port;

        Record(String target, int port) {
            this.target = target;
            this.port = port;
        }
    }

    private static final Random RANDOM = new Random();

    private SrvResolver() {
    }

    public static Record resolve(String host) {
        if (host == null || host.isEmpty() || host.indexOf(':') >= 0) {
            return null;
        }
        String query = "_minecraft._tcp." + host;
        Record record = resolveWith("https://cloudflare-dns.com/dns-query?name="
                + urlEncode(query) + "&type=SRV");
        if (record == null) {
            // AliDNS's JSON endpoint works when Cloudflare is unavailable (common in mainland China).
            record = resolveWith("https://dns.alidns.com/resolve?name="
                    + urlEncode(query) + "&type=SRV");
        }
        if (record == null) {
            record = resolveWith("https://dns.google/resolve?name="
                    + urlEncode(query) + "&type=SRV");
        }
        return record;
    }

    private static Record resolveWith(String endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(1300);
            connection.setReadTimeout(1300);
            connection.setRequestProperty("Accept", "application/dns-json");
            connection.setRequestProperty("User-Agent", "CubeProbe/1.0");
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > 65536) {
                    return null;
                }
            }
            String body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            return parse(body);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Record parse(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray answers = root.optJSONArray("Answer");
            if (answers == null) {
                answers = root.optJSONArray("Answers");
            }
            if (answers == null) {
                return null;
            }
            List<Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.optJSONObject(i);
                if (answer == null) {
                    continue;
                }
                if (answer.optInt("type", -1) != 33) {
                    continue;
                }
                String data = answer.optString("data", "");
                String[] parts = data.trim().split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                int priority = parseInt(parts[0], 0);
                int weight = parseInt(parts[1], 0);
                int port = parseInt(parts[2], -1);
                String target = parts[3];
                while (target.endsWith(".")) {
                    target = target.substring(0, target.length() - 1);
                }
                if (port < 1 || port > 65535 || target.isEmpty()) {
                    continue;
                }
                candidates.add(new Candidate(priority, weight, target, port));
            }
            if (candidates.isEmpty()) {
                return null;
            }
            int minPriority = Integer.MAX_VALUE;
            for (Candidate candidate : candidates) {
                minPriority = Math.min(minPriority, candidate.priority);
            }
            List<Candidate> preferred = new ArrayList<>();
            int totalWeight = 0;
            for (Candidate candidate : candidates) {
                if (candidate.priority == minPriority) {
                    preferred.add(candidate);
                    totalWeight += Math.max(1, candidate.weight);
                }
            }
            int roll = RANDOM.nextInt(Math.max(1, totalWeight));
            for (Candidate candidate : preferred) {
                roll -= Math.max(1, candidate.weight);
                if (roll < 0) {
                    return new Record(candidate.target, candidate.port);
                }
            }
            Candidate last = preferred.get(preferred.size() - 1);
            return new Record(last.target, last.port);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static class Candidate {
        final int priority;
        final int weight;
        final String target;
        final int port;

        Candidate(int priority, int weight, String target, int port) {
            this.priority = priority;
            this.weight = weight;
            this.target = target;
            this.port = port;
        }
    }
}
