package de.tum.cit.aet.hephaestus.core.auth.oauth;

import java.util.Locale;
import org.springframework.lang.Nullable;

/**
 * Validates {@code returnTo} URLs before we 302 the user to one. Open-redirect defence.
 *
 * <p>Rules:
 * <ul>
 *   <li>Must start with a single {@code /} (rejects {@code //evil.com} and {@code /\evil}).</li>
 *   <li>No control characters (CR, LF, NUL) — log-injection defence.</li>
 *   <li>No {@code javascript:} / {@code data:} / {@code vbscript:} prefixes after trim.</li>
 *   <li>Path-only — query-string allowed, fragment allowed.</li>
 * </ul>
 *
 * <p>Mirrored client-side in {@code webapp/src/lib/url.ts} (later commit). Defence in depth
 * — the server is authoritative.
 */
public final class ReturnToValidator {

    private static final String FALLBACK = "/";

    private ReturnToValidator() {}

    /** Returns the input if safe, else the safe fallback {@code "/"}. Never throws. */
    public static String safeOrFallback(@Nullable String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return FALLBACK;
        }
        String trimmed = returnTo.trim();
        // Reject control characters anywhere.
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return FALLBACK;
            }
        }
        // Reject dangerous URI schemes — case insensitive, defensive lower().
        // Locale.ROOT so a Turkish-locale 'I'→'ı' fold can never let "JAVASCRIPT:" slip past.
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (
            lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("vbscript:") ||
            lower.startsWith("file:")
        ) {
            return FALLBACK;
        }
        // Must start with exactly one '/' and the next char must NOT be '/' or '\'
        // (which the browser may resolve as a protocol-relative URL).
        if (!trimmed.startsWith("/")) {
            return FALLBACK;
        }
        if (trimmed.length() >= 2) {
            char second = trimmed.charAt(1);
            if (second == '/' || second == '\\') {
                return FALLBACK;
            }
        }
        return trimmed;
    }
}
