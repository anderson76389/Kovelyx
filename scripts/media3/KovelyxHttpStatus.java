package androidx.media3.datasource;

/** Keep the real HTTP status, even for an error returned by the local torrent proxy. */
public final class KovelyxHttpStatus {
    private KovelyxHttpStatus() {}

    public static int fromHeaders(String headers) {
        if (headers == null) return 400;
        int firstEnd = headers.indexOf('\n');
        String line = (firstEnd < 0 ? headers : headers.substring(0, firstEnd)).trim();
        // The zero-copy socket sends HTTP/1.1. Never parse a status from the body.
        if (!line.startsWith("HTTP/")) return 400;
        int separator = line.indexOf(' ');
        if (separator < 0) return 400;
        int start = separator + 1;
        while (start < line.length() && line.charAt(start) == ' ') start++;
        if (start + 3 > line.length()) return 400;
        for (int i = start; i < start + 3; i++) {
            if (line.charAt(i) < '0' || line.charAt(i) > '9') return 400;
        }
        if (start + 3 < line.length() && line.charAt(start + 3) != ' ') return 400;
        int code = (line.charAt(start) - '0') * 100 +
                (line.charAt(start + 1) - '0') * 10 + (line.charAt(start + 2) - '0');
        return code >= 100 && code <= 599 ? code : 400;
    }
}
