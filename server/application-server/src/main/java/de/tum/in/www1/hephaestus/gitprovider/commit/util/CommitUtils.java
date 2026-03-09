package de.tum.in.www1.hephaestus.gitprovider.commit.util;

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
    public String normalizeString(Object value) {
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
    public String buildCommitUrl(String nameWithOwner, String sha) {
        return "https://github.com/" + nameWithOwner + "/commit/" + sha;
    }

    /**
     * Builds the web URL for a commit on a GitLab instance.
     *
     * @param serverUrl     the GitLab server base URL (e.g., {@code "https://gitlab.lrz.de"})
     * @param nameWithOwner project path ({@code "group/subgroup/project"})
     * @param sha           full commit SHA
     * @return the commit URL on GitLab
     */
    public String buildGitLabCommitUrl(String serverUrl, String nameWithOwner, String sha) {
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        return base + "/" + nameWithOwner + "/-/commit/" + sha;
    }
}
