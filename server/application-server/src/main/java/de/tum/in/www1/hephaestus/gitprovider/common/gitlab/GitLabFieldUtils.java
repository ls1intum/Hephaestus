package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import org.springframework.lang.Nullable;

/**
 * Shared utilities for extracting typed values from GitLab GraphQL/webhook
 * response maps where values may arrive as String, Number, or null.
 */
public final class GitLabFieldUtils {

    private GitLabFieldUtils() {}

    @Nullable
    public static Integer toInteger(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static String asString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }
}
