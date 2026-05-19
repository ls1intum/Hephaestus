package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.Optional;

/**
 * Utility class for parsing GitHub repository names in "owner/name" format.
 * <p>
 * Provides a type-safe way to parse repository identifiers from GitHub's
 * "nameWithOwner" format into separate owner and name components.
 */
public final class GitHubRepositoryNameParser {

    private GitHubRepositoryNameParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Record representing a parsed repository owner and name.
     *
     * @param owner the repository owner (user or organization)
     * @param name  the repository name
     */
    public record RepositoryOwnerAndName(String owner, String name) {}

    /**
     * Parses a repository name in "owner/name" format.
     *
     * @param nameWithOwner the repository name with owner, e.g., "octocat/Hello-World"
     * @return an Optional containing the parsed owner and name, or empty if invalid
     */
    public static Optional<RepositoryOwnerAndName> parse(String nameWithOwner) {
        if (nameWithOwner == null || !nameWithOwner.contains("/")) {
            return Optional.empty();
        }
        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RepositoryOwnerAndName(parts[0], parts[1]));
    }
}
