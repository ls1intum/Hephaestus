package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.gitlab;

import org.springframework.lang.Nullable;

/**
 * Shared utilities for extracting typed values from GitLab GraphQL/webhook
 * response maps where values may arrive as String, Number, or null.
 */
final class GitLabFieldUtils {

    private GitLabFieldUtils() {}

    @Nullable
    static Integer toInteger(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    static String asString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }
}
