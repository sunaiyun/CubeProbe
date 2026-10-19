package com.cubeprobe.mc.net;

/** Typed connection failure so the UI can explain what went wrong. */
public class QueryException extends Exception {

    public enum Kind {
        INVALID,
        UNKNOWN_HOST,
        TIMEOUT,
        REFUSED,
        NETWORK,
        PROTOCOL,
        OTHER
    }

    private final Kind kind;

    public QueryException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static QueryException wrap(Exception e) {
        if (e instanceof QueryException) {
            return (QueryException) e;
        }
        String text = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (e instanceof java.net.UnknownHostException) {
            return new QueryException(Kind.UNKNOWN_HOST, "unknown host", e);
        }
        if (e instanceof java.net.SocketTimeoutException
                || text.contains("timed out") || text.contains("timeout")) {
            return new QueryException(Kind.TIMEOUT, "timeout", e);
        }
        if (e instanceof java.net.ConnectException || text.contains("refused")) {
            return new QueryException(Kind.REFUSED, "connection refused", e);
        }
        if (e instanceof java.net.NoRouteToHostException
                || e instanceof java.net.PortUnreachableException
                || text.contains("network is unreachable")
                || text.contains("no route")) {
            return new QueryException(Kind.NETWORK, "no route", e);
        }
        if (e instanceof java.net.SocketException || e instanceof java.io.EOFException
                || e instanceof java.io.IOException) {
            return new QueryException(Kind.OTHER, e.getMessage(), e);
        }
        return new QueryException(Kind.OTHER, e.getMessage(), e);
    }
}
