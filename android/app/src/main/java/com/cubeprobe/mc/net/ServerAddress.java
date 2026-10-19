package com.cubeprobe.mc.net;

import java.net.IDN;
import java.util.Locale;

/** Parsed Minecraft server address, including IPv6 and custom-port support. */
public final class ServerAddress {

    public final String host;
    public final int port;
    public final boolean explicitPort;

    private ServerAddress(String host, int port, boolean explicitPort) {
        this.host = host;
        this.port = port;
        this.explicitPort = explicitPort;
    }

    public static int defaultPort(int mode) {
        return mode == com.cubeprobe.mc.ui.ModeSwitch.MODE_BEDROCK ? 19132 : 25565;
    }

    public static ServerAddress parse(String raw, int mode) throws QueryException {
        if (raw == null) {
            throw new QueryException(QueryException.Kind.INVALID, "empty address", null);
        }
        String input = raw.trim();
        if (input.isEmpty()) {
            throw new QueryException(QueryException.Kind.INVALID, "empty address", null);
        }

        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.startsWith("minecraft://")) {
            input = input.substring("minecraft://".length());
        } else if (lower.startsWith("mc://")) {
            input = input.substring("mc://".length());
        } else if (lower.startsWith("https://")) {
            input = input.substring("https://".length());
        } else if (lower.startsWith("http://")) {
            input = input.substring("http://".length());
        }
        int slash = input.indexOf('/');
        if (slash >= 0) {
            input = input.substring(0, slash);
        }
        input = input.trim();
        if (input.isEmpty() || input.length() > 260) {
            throw new QueryException(QueryException.Kind.INVALID, "bad address", null);
        }

        boolean explicit = false;
        String host;
        int port = defaultPort(mode);

        if (input.startsWith("[")) {
            int end = input.indexOf(']');
            if (end < 0) {
                throw new QueryException(QueryException.Kind.INVALID, "bad IPv6", null);
            }
            host = input.substring(1, end);
            String rest = input.substring(end + 1);
            if (rest.startsWith(":")) {
                port = parsePort(rest.substring(1));
                explicit = true;
            } else if (!rest.isEmpty()) {
                throw new QueryException(QueryException.Kind.INVALID, "bad IPv6 suffix", null);
            }
        } else {
            int colonCount = countColons(input);
            if (colonCount == 1) {
                int colon = input.lastIndexOf(':');
                String after = input.substring(colon + 1);
                if (!after.isEmpty() && isDigits(after)) {
                    host = input.substring(0, colon);
                    port = parsePort(after);
                    explicit = true;
                } else {
                    throw new QueryException(QueryException.Kind.INVALID, "bad port", null);
                }
            } else if (colonCount > 1) {
                // Bare IPv6 literal. A default/custom port cannot be expressed without brackets.
                host = input;
            } else {
                host = input;
            }
        }

        host = host.trim();
        if (host.startsWith("[") || host.endsWith("]") || host.endsWith(".")) {
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
        }
        if (host.isEmpty() || host.length() > 253) {
            throw new QueryException(QueryException.Kind.INVALID, "bad host", null);
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '\\' || c == '@'
                    || c == '?' || c == '#') {
                throw new QueryException(QueryException.Kind.INVALID, "bad host char", null);
            }
        }

        if (host.indexOf(':') < 0) {
            try {
                host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            } catch (IllegalArgumentException e) {
                throw new QueryException(QueryException.Kind.INVALID, "bad IDN", e);
            }
        }

        return new ServerAddress(host, port, explicit);
    }

    private static int parsePort(String value) throws QueryException {
        if (value.isEmpty() || !isDigits(value) || value.length() > 5) {
            throw new QueryException(QueryException.Kind.INVALID, "bad port", null);
        }
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new QueryException(QueryException.Kind.INVALID, "bad port", e);
        }
        if (port < 1 || port > 65535) {
            throw new QueryException(QueryException.Kind.INVALID, "bad port range", null);
        }
        return port;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int countColons(String value) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ':') {
                n++;
            }
        }
        return n;
    }

    public boolean isIpLiteral() {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (!(Character.isDigit(c) || c == '.')) {
                return false;
            }
        }
        return !host.isEmpty();
    }

    public String display() {
        String rendered = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        int shownPort = port;
        return rendered + ":" + shownPort;
    }

    public String displayWithoutDefault(int defaultPort) {
        String rendered = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return explicitPort ? rendered + ":" + port : rendered;
    }

    public String connectHost() {
        return host;
    }
}
