package com.cubeprobe.mc.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Bedrock Edition unconnected ping (RakNet offline message, 0x01/0x1C).
 * The packet is sent over UDP directly to the server; no external API is used.
 */
public final class BedrockPinger {

    private static final byte[] MAGIC = {
            0x00, (byte) 0xFF, (byte) 0xFF, 0x00,
            (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
            (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD,
            0x12, 0x34, 0x56, 0x78
    };

    private static final Random RANDOM = new Random();
    private static final int ATTEMPTS = 3;
    private static final int ATTEMPT_TIMEOUT_MS = 1200;

    private BedrockPinger() {
    }

    public static BedrockStatus query(ServerAddress address, int timeoutMs) throws QueryException {
        InetAddress target;
        try {
            target = InetAddress.getByName(address.host);
        } catch (UnknownHostException e) {
            throw new QueryException(QueryException.Kind.UNKNOWN_HOST, "unknown host", e);
        }

        int wait = Math.max(700, Math.min(ATTEMPT_TIMEOUT_MS, timeoutMs));
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(wait);
            long guid = RANDOM.nextLong();
            byte[] ping = buildPing(System.currentTimeMillis(), guid);
            DatagramPacket outgoing = new DatagramPacket(ping, ping.length, target, address.port);

            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long start = System.nanoTime();
                socket.send(outgoing);
                long deadline = start + wait * 1_000_000L;
                while (true) {
                    long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                    if (remainingMs <= 0) {
                        break;
                    }
                    socket.setSoTimeout((int) Math.max(1, remainingMs));
                    byte[] buffer = new byte[4096];
                    DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(incoming);
                    } catch (SocketTimeoutException timeout) {
                        break;
                    }
                    long latency = Math.max(1, (System.nanoTime() - start) / 1_000_000L);
                    BedrockStatus status = parse(address, incoming.getData(),
                            incoming.getOffset(), incoming.getLength(), latency);
                    if (status != null) {
                        return status;
                    }
                }
            }
            throw new QueryException(QueryException.Kind.TIMEOUT, "no bedrock pong", null);
        } catch (PortUnreachableException e) {
            throw new QueryException(QueryException.Kind.REFUSED, "port unreachable", e);
        } catch (ConnectException e) {
            throw new QueryException(QueryException.Kind.REFUSED, "connection refused", e);
        } catch (java.net.SocketException e) {
            throw QueryException.wrap(e);
        } catch (IOException e) {
            throw QueryException.wrap(e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static byte[] buildPing(long timestamp, long guid) {
        ByteBuffer buffer = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x01);
        buffer.putLong(timestamp);
        buffer.put(MAGIC);
        buffer.putLong(guid);
        return buffer.array();
    }

    private static BedrockStatus parse(ServerAddress address, byte[] data, int offset, int length,
                                       long latency) {
        if (length < 35 || (data[offset] & 0xFF) != 0x1C) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
        buffer.get();
        buffer.getLong();
        buffer.getLong();
        byte[] magic = new byte[16];
        buffer.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                return null;
            }
        }
        int textLength = buffer.getShort() & 0xFFFF;
        if (textLength <= 0 || textLength > buffer.remaining()) {
            return null;
        }
        byte[] textBytes = new byte[textLength];
        buffer.get(textBytes);
        String text = new String(textBytes, StandardCharsets.UTF_8);
        String[] fields = text.split(";", -1);
        if (fields.length < 6) {
            return null;
        }
        String motd = fields.length > 1 ? fields[1] : "";
        int protocol = parseInt(fields.length > 2 ? fields[2] : "", -1);
        String version = fields.length > 3 ? fields[3] : "";
        int online = parseInt(fields.length > 4 ? fields[4] : "", 0);
        int max = parseInt(fields.length > 5 ? fields[5] : "", 0);
        String serverId = fields.length > 6 ? fields[6] : "";
        String subMotd = fields.length > 7 ? fields[7] : "";
        String gameMode = fields.length > 8 ? fields[8] : "";
        int gameModeId = parseInt(fields.length > 9 ? fields[9] : "", -1);
        int portV4 = parseInt(fields.length > 10 ? fields[10] : "", 0);
        int portV6 = parseInt(fields.length > 11 ? fields[11] : "", 0);
        return new BedrockStatus(address.host, address.port, motd, subMotd, version, protocol,
                online, max, serverId, gameMode, gameModeId, portV4, portV6, latency);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
