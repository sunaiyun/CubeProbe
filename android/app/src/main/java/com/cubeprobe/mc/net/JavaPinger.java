package com.cubeprobe.mc.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direct Java Edition Server List Ping (modern 1.7+ with a 1.6 legacy fallback).
 * No third-party service sits between the app and the server.
 */
public final class JavaPinger {

    private static final int MAX_PACKET = 2 * 1024 * 1024;
    private static final int MAX_FAVICON = 350_000;

    private JavaPinger() {
    }

    public static JavaStatus query(ServerAddress address, String connectHost, int connectPort,
                                   int timeoutMs) throws QueryException {
        QueryException modernError;
        try {
            return queryModern(address, connectHost, connectPort, timeoutMs);
        } catch (QueryException e) {
            modernError = e;
            if (e.getKind() == QueryException.Kind.UNKNOWN_HOST
                    || e.getKind() == QueryException.Kind.NETWORK
                    || e.getKind() == QueryException.Kind.INVALID) {
                throw e;
            }
        }

        try {
            return queryLegacy(address, connectHost, connectPort, Math.max(1800, timeoutMs * 2 / 3));
        } catch (QueryException legacyError) {
            if (legacyError.getKind() == QueryException.Kind.TIMEOUT
                    || legacyError.getKind() == QueryException.Kind.OTHER) {
                throw legacyError;
            }
            throw modernError;
        }
    }

    private static JavaStatus queryModern(ServerAddress address, String connectHost, int connectPort,
                                          int timeoutMs) throws QueryException {
        Socket socket = new Socket();
        long start = System.nanoTime();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(connectHost, connectPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            OutputStream out = rawOut;

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, -1);
            writeString(handshake, address.host);
            handshake.write((connectPort >> 8) & 0xFF);
            handshake.write(connectPort & 0xFF);
            writeVarInt(handshake, 1);
            writeFramed(out, handshake.toByteArray());

            ByteArrayOutputStream request = new ByteArrayOutputStream();
            writeVarInt(request, 0x00);
            writeFramed(out, request.toByteArray());
            out.flush();

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_PACKET) {
                throw new QueryException(QueryException.Kind.PROTOCOL, "bad packet length", null);
            }
            byte[] payload = new byte[length];
            in.readFully(payload);

            int[] cursor = {0};
            int packetId = readVarInt(payload, cursor);
            if (packetId != 0x00) {
                throw new QueryException(QueryException.Kind.PROTOCOL, "unexpected packet id", null);
            }
            String json = readString(payload, cursor);
            long latency = Math.max(1, (System.nanoTime() - start) / 1_000_000L);
            return parseStatus(address, connectPort, json, latency);
        } catch (QueryException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new QueryException(QueryException.Kind.TIMEOUT, "read timeout", e);
        } catch (ConnectException e) {
            throw new QueryException(QueryException.Kind.REFUSED, "connection refused", e);
        } catch (UnknownHostException e) {
            throw new QueryException(QueryException.Kind.UNKNOWN_HOST, "unknown host", e);
        } catch (NoRouteToHostException e) {
            throw new QueryException(QueryException.Kind.NETWORK, "no route", e);
        } catch (EOFException e) {
            throw new QueryException(QueryException.Kind.PROTOCOL, "connection closed", e);
        } catch (IOException e) {
            throw QueryException.wrap(e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static JavaStatus parseStatus(ServerAddress address, int connectPort, String json,
                                          long latency) throws QueryException {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject version = root.optJSONObject("version");
            JSONObject players = root.optJSONObject("players");
            String versionName = version == null ? "未知版本" : version.optString("name", "未知版本");
            int protocol = version == null ? -1 : version.optInt("protocol", -1);
            int online = players == null ? 0 : players.optInt("online", 0);
            int max = players == null ? 0 : players.optInt("max", 0);

            List<String> sample = new ArrayList<>();
            JSONArray sampleArray = players == null ? null : players.optJSONArray("sample");
            if (sampleArray != null) {
                int limit = Math.min(sampleArray.length(), 80);
                for (int i = 0; i < limit; i++) {
                    Object item = sampleArray.opt(i);
                    if (item instanceof JSONObject) {
                        String name = ((JSONObject) item).optString("name", "");
                        if (!name.isEmpty()) {
                            sample.add(name);
                        }
                    } else if (item != null) {
                        sample.add(String.valueOf(item));
                    }
                }
            }

            Object description = root.opt("description");
            if (description == null || description == JSONObject.NULL) {
                description = "A Minecraft Server";
            }

            String favicon = root.optString("favicon", "");
            if (favicon.startsWith("data:image/png;base64,")) {
                favicon = favicon.substring("data:image/png;base64,".length());
            }
            if (favicon.length() > MAX_FAVICON) {
                favicon = "";
            }

            String software = null;
            JSONObject modInfo = root.optJSONObject("modinfo");
            if (modInfo == null) {
                modInfo = root.optJSONObject("modInfo");
            }
            if (modInfo != null) {
                String type = modInfo.optString("type", "");
                if (type != null && !type.isEmpty() && !"VANILLA".equalsIgnoreCase(type)) {
                    software = type;
                } else {
                    software = "Modded";
                }
            }
            if (software == null && root.optJSONObject("forgeData") != null) {
                software = "Forge";
            }

            boolean secure = root.optBoolean("enforcesSecureChat", false);
            return new JavaStatus(address.host, connectPort, versionName, protocol,
                    online, max, sample, description, favicon, software, secure, false, latency);
        } catch (Exception e) {
            throw new QueryException(QueryException.Kind.PROTOCOL, "invalid status json", e);
        }
    }

    private static JavaStatus queryLegacy(ServerAddress address, String connectHost, int connectPort,
                                          int timeoutMs) throws QueryException {
        Socket socket = new Socket();
        long start = System.nanoTime();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(connectHost, connectPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.write(0xFE);
            out.write(0x01);
            out.flush();

            int first = in.readUnsignedByte();
            if (first != 0xFF) {
                throw new QueryException(QueryException.Kind.PROTOCOL, "not a legacy response", null);
            }
            int chars = in.readUnsignedShort();
            if (chars <= 0 || chars > 32767) {
                throw new QueryException(QueryException.Kind.PROTOCOL, "bad legacy length", null);
            }
            byte[] encoded = new byte[chars * 2];
            in.readFully(encoded);
            String text = new String(encoded, StandardCharsets.UTF_16BE);
            long latency = Math.max(1, (System.nanoTime() - start) / 1_000_000L);

            String[] fields = text.split("\u0000", -1);
            String motd = fields.length > 3 ? fields[3] : text;
            String versionName = fields.length > 2 ? fields[2] : "旧版服务器";
            int protocol = parseInt(fields.length > 1 ? fields[1] : "", -1);
            int online = parseInt(fields.length > 4 ? fields[4] : "", 0);
            int max = parseInt(fields.length > 5 ? fields[5] : "", 0);
            return new JavaStatus(address.host, connectPort, versionName, protocol,
                    online, max, new ArrayList<>(), motd, null, null, false, true, latency);
        } catch (QueryException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new QueryException(QueryException.Kind.TIMEOUT, "legacy timeout", e);
        } catch (ConnectException e) {
            throw new QueryException(QueryException.Kind.REFUSED, "legacy refused", e);
        } catch (UnknownHostException e) {
            throw new QueryException(QueryException.Kind.UNKNOWN_HOST, "unknown host", e);
        } catch (NoRouteToHostException e) {
            throw new QueryException(QueryException.Kind.NETWORK, "no route", e);
        } catch (IOException e) {
            throw QueryException.wrap(e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void writeFramed(OutputStream out, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        writeVarInt(frame, payload.length);
        frame.write(payload);
        out.write(frame.toByteArray());
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException("unexpected end of stream");
            }
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 35) {
                throw new IOException("varint too large");
            }
        }
    }

    private static int readVarInt(byte[] data, int[] cursor) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            if (cursor[0] >= data.length) {
                throw new EOFException("truncated varint");
            }
            int current = data[cursor[0]++] & 0xFF;
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 35) {
                throw new IOException("varint too large");
            }
        }
    }

    private static String readString(byte[] data, int[] cursor) throws IOException {
        int length = readVarInt(data, cursor);
        if (length < 0 || length > data.length - cursor[0] || length > MAX_PACKET) {
            throw new EOFException("bad string length");
        }
        String value = new String(data, cursor[0], length, StandardCharsets.UTF_8);
        cursor[0] += length;
        return value;
    }
}
