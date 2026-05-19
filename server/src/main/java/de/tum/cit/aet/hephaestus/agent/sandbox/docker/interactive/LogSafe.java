package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;

/**
 * Strip C0 + C1 control chars and BiDi overrides from caller- or runner-supplied strings before
 * passing them to a logging framework: defeats log-line injection (CR/LF), terminal-escape attacks
 * (ANSI CSI via 0x1B or U+009B), and Trojan Source-style direction reversals.
 */
final class LogSafe {

    private static final int MAX_LEN = 128;

    private LogSafe() {}

    static String sanitise(String value) {
        return sanitise(value, MAX_LEN);
    }

    static String sanitise(String value, int maxLen) {
        if (value == null) {
            return "<null>";
        }
        int len = Math.min(value.length(), maxLen);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (isUnsafe(c)) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        if (value.length() > maxLen) {
            sb.append("…");
        }
        return sb.toString();
    }

    private static boolean isUnsafe(char c) {
        if (c < 0x20 || c == 0x7F) return true; // C0 + DEL
        if (c >= 0x80 && c <= 0x9F) return true; // C1 (includes CSI U+009B)
        if (c >= 0x202A && c <= 0x202E) return true; // LRE/RLE/PDF/LRO/RLO
        if (c >= 0x2066 && c <= 0x2069) return true; // LRI/RLI/FSI/PDI
        return false;
    }
}
