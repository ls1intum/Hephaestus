package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import lombok.experimental.UtilityClass;
import org.springframework.lang.Nullable;

/**
 * Shared helper methods used across commit-related services.
 */
@UtilityClass
public class CommitUtils {

    /**
     * Normalizes a value to a trimmed, non-empty string or {@code null}.
     *
     * @param value an object (typically obtained from a parsed JSON/GraphQL map)
     * @return the trimmed string, or {@code null} if the input is not a {@link String} or is blank
     */
    @Nullable
    static String normalizeString(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Builds the canonical GitHub web URL for a commit.
     *
     * @param nameWithOwner repository full name ({@code "owner/repo"})
     * @param sha           full commit SHA
     * @return the commit URL on GitHub
     */
    static String buildCommitUrl(String nameWithOwner, String sha) {
        return "https://github.com/" + nameWithOwner + "/commit/" + sha;
    }
}
