package de.tum.cit.aet.hephaestus.core.auth.oauth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Validates {@code returnTo} URLs before we 302 the user to one. Open-redirect defence.
 *
 * <p>Rules (applied to the FULLY percent-decoded value — see below):
 * <ul>
 *   <li>Must start with a single {@code /} (rejects {@code //evil.com} and {@code /\evil}).</li>
 *   <li>No control characters (CR, LF, NUL, TAB) — log-injection / header-split defence.</li>
 *   <li>No {@code javascript:} / {@code data:} / {@code vbscript:} prefixes after trim.</li>
 *   <li>Path-only — query-string allowed, fragment allowed.</li>
 * </ul>
 *
 * <h2>Decode-then-check</h2>
 * Browsers (and our own redirect) resolve percent-encoding, so a raw value like {@code /%2f%2fevil.com}
 * or {@code /%09/evil} would pass a naive literal check yet decode to {@code //evil.com} / a TAB-prefixed
 * path at navigation time. We therefore percent-decode the input (repeatedly, until it stops changing,
 * bounded to a few passes so a crafted self-expanding sequence can never loop) and run every check
 * against the decoded form. The original (non-decoded) value is what we return when safe, so a
 * legitimately encoded query string is preserved verbatim.
 *
 * <p>Mirrored client-side in {@code webapp/src/integrations/auth/guard.ts}. Defence in depth — the
 * server is authoritative.
 */
public final class ReturnToValidator {

    private static final String FALLBACK = "/";

    /** Bound on decode passes — defends against a maliciously self-expanding encoded sequence. */
    private static final int MAX_DECODE_PASSES = 3;

    private ReturnToValidator() {}

    /** Returns the input if safe, else the safe fallback {@code "/"}. Never throws. */
    public static String safeOrFallback(@Nullable String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return FALLBACK;
        }
        String trimmed = returnTo.trim();
        // Validate against the fully percent-decoded form so encoded slashes / control chars / schemes
        // cannot smuggle past the literal checks (e.g. /%2f%2fevil.com, /%09/evil).
        String decoded = fullyDecode(trimmed);
        return isSafe(decoded) ? trimmed : FALLBACK;
    }

    /** Percent-decode until the value stabilises (bounded). On any malformed input, fail safe. */
    private static String fullyDecode(String value) {
        String current = value;
        for (int pass = 0; pass < MAX_DECODE_PASSES; pass++) {
            String next;
            try {
                next = URLDecoder.decode(current, StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                return "";
            }
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return "";
    }

    private static boolean isSafe(String candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        // Reject control characters anywhere (URLDecoder turns '+' into space; a bare space is fine,
        // but CR/LF/TAB/NUL/DEL are not).
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return false;
            }
        }
        // Reject dangerous URI schemes — case insensitive, defensive lower().
        // Locale.ROOT so a Turkish-locale 'I'→'ı' fold can never let "JAVASCRIPT:" slip past.
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:")
                || lower.startsWith("data:")
                || lower.startsWith("vbscript:")
                || lower.startsWith("file:")) {
            return false;
        }
        // Must start with exactly one '/' and the next char must NOT be '/' or '\'
        // (which the browser may resolve as a protocol-relative URL).
        if (!candidate.startsWith("/")) {
            return false;
        }
        if (candidate.length() >= 2) {
            char second = candidate.charAt(1);
            if (second == '/' || second == '\\') {
                return false;
            }
        }
        return true;
    }
}
