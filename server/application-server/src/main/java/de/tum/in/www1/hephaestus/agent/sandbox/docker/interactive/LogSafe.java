package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

/** Strip C0/C1 control chars from caller-supplied identifiers — defeats log-line injection. */
final class LogSafe {

    private static final int MAX_LEN = 128;

    private LogSafe() {}

    static String sanitise(String value) {
        if (value == null) {
            return "<null>";
        }
        int len = Math.min(value.length(), MAX_LEN);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
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
