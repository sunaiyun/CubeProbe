package com.cubeprobe.mc.net;

/** Immutable status returned by a Bedrock Edition server over RakNet. */
public final class BedrockStatus {

    public final String host;
    public final int port;
    public final String motd;
    public final String subMotd;
    public final String versionName;
    public final int protocol;
    public final int online;
    public final int max;
    public final String serverId;
    public final String gameMode;
    public final int gameModeId;
    public final int portV4;
    public final int portV6;
    public final long latencyMs;

    public BedrockStatus(String host, int port, String motd, String subMotd,
                         String versionName, int protocol, int online, int max,
                         String serverId, String gameMode, int gameModeId,
                         int portV4, int portV6, long latencyMs) {
        this.host = host;
        this.port = port;
        this.motd = motd == null ? "" : motd;
        this.subMotd = subMotd == null ? "" : subMotd;
        this.versionName = versionName == null || versionName.isEmpty() ? "未知版本" : versionName;
        this.protocol = protocol;
        this.online = Math.max(0, online);
        this.max = Math.max(0, max);
        this.serverId = serverId == null ? "" : serverId;
        this.gameMode = gameMode == null ? "" : gameMode;
        this.gameModeId = gameModeId;
        this.portV4 = portV4;
        this.portV6 = portV6;
        this.latencyMs = latencyMs;
    }
}
