package com.cubeprobe.mc.net;

import java.util.ArrayList;
import java.util.List;

/** Immutable status returned by a Java Edition server. */
public final class JavaStatus {

    public final String host;
    public final int port;
    public final String versionName;
    public final int protocol;
    public final int online;
    public final int max;
    public final List<String> sample;
    public final Object description;
    public final String faviconBase64;
    public final String software;
    public final boolean secureChat;
    public final boolean legacy;
    public final long latencyMs;

    public JavaStatus(String host, int port, String versionName, int protocol,
                      int online, int max, List<String> sample, Object description,
                      String faviconBase64, String software, boolean secureChat,
                      boolean legacy, long latencyMs) {
        this.host = host;
        this.port = port;
        this.versionName = versionName == null || versionName.isEmpty() ? "未知版本" : versionName;
        this.protocol = protocol;
        this.online = Math.max(0, online);
        this.max = Math.max(0, max);
        this.sample = sample == null ? new ArrayList<>() : sample;
        this.description = description;
        this.faviconBase64 = faviconBase64;
        this.software = software;
        this.secureChat = secureChat;
        this.legacy = legacy;
        this.latencyMs = latencyMs;
    }
}
