package de.tum.cit.aet.hephaestus.core.auth.oauth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Validates relative return paths before redirecting the user. */
public final class ReturnToValidator {

    private static final String FALLBACK = "/";

    private static final int MAX_DECODE_PASSES = 3;

    private ReturnToValidator() {}

    /** Returns the input if safe, else the safe fallback {@code "/"}. Never throws. */
    public static String safeOrFallback(@Nullable String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return FALLBACK;
        }
        String trimmed = returnTo.trim();
        String decoded = fullyDecode(trimmed);
        return isSafe(decoded) ? trimmed : FALLBACK;
    }

    /** Decodes at most three times and rejects malformed input or values that do not stabilize. */
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
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return false;
            }
        }
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
