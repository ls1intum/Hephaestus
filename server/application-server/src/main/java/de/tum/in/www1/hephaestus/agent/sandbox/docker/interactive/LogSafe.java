package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

/**
 * Sanitises caller-supplied identifiers (userId, workspaceId, container ID) before they reach
 * SLF4J. Caller-supplied means attacker-influenced: a userId containing
 * {@code "alice\n[ERROR] forged line"} would otherwise appear in logs as an extra log line.
 */
final class LogSafe {

    private static final int MAX_LEN = 128;

    private LogSafe() {}

    /** Returns {@code value} with control chars (CR, LF, NUL, etc.) replaced and overlong input truncated. */
    static String sanitise(String value) {
        if (value == null) {
            return "<null>";
        }
        int len = Math.min(value.length(), MAX_LEN);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            // Replace any C0/C1 control char or DEL. Keep printable ASCII + non-ASCII Unicode as-is.
            if ((c < 0x20) || c == 0x7F) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        if (value.length() > MAX_LEN) {
            sb.append("…");
        }
        return sb.toString();
    }
}
