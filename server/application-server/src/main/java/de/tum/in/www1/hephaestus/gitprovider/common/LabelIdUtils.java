package de.tum.in.www1.hephaestus.gitprovider.common;

/**
 * Shared utility for generating deterministic label IDs.
 * <p>
 * When a Git provider's API does not expose a stable database ID for labels
 * (e.g., GitHub GraphQL, GitLab group-level labels), a deterministic negative ID
 * is generated from the repository ID and label name. Negative IDs prevent
 * collisions with real provider-assigned positive IDs.
 */
public final class LabelIdUtils {

    private LabelIdUtils() {
        // Utility class
    }

    /**
     * Generates a deterministic negative ID from (repositoryId, labelName).
     * <p>
     * Uses bit shifting to combine the two components without collision:
     * repository ID occupies the upper 32 bits, label name hash the lower 32 bits.
     *
     * @param repositoryId the repository's database ID
     * @param labelName    the label's name
     * @return a deterministic negative Long ID
     */
    public static long generateDeterministicId(long repositoryId, String labelName) {
        long combined = (repositoryId << 32) | (labelName.hashCode() & 0xFFFFFFFFL);
        return -combined;
    }
}
