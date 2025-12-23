package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared utilities for parsing GraphQL responses.
 * <p>
 * Consolidates common parsing patterns to eliminate duplication
 * across sync services (DRY principle).
 */
public final class GraphQlParsingUtils {

    /**
     * Default page size for GraphQL pagination.
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Default timeout for GraphQL requests.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private GraphQlParsingUtils() {
        // Utility class
    }

    /**
     * Parses a repository name into owner and name components.
     *
     * @param nameWithOwner the full repository name (owner/repo)
     * @return array of [owner, name], or null if invalid
     */
    public static String[] parseRepositoryName(String nameWithOwner) {
        if (nameWithOwner == null) {
            return null;
        }
        String[] parts = nameWithOwner.split("/");
        return parts.length == 2 ? parts : null;
    }

    /**
     * Safely parses a value as Long.
     */
    public static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Safely parses a value as Integer with a default.
     */
    public static int parseIntOrDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Safely parses a value as boolean.
     */
    public static boolean parseBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    /**
     * Safely parses an ISO-8601 instant from a string.
     */
    public static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Safely gets a string value from a map.
     */
    public static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Converts a GitHub GraphQL issue/PR state to lowercase.
     */
    public static String convertState(String state) {
        if (state == null) {
            return "open";
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> "open";
            case "CLOSED" -> "closed";
            case "MERGED" -> "closed";
            default -> state.toLowerCase();
        };
    }

    /**
     * Converts a GitHub GraphQL milestone state to lowercase.
     */
    public static String convertMilestoneState(String state) {
        if (state == null) {
            return "open";
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> "open";
            case "CLOSED" -> "closed";
            default -> state.toLowerCase();
        };
    }

    // ========== ENUM CONVERSION METHODS ==========
    // These methods provide type-safe conversion from GitHub API strings to entity enums.
    // Centralizing these conversions follows DRY and provides consistent behavior.

    /**
     * Converts a GitHub API state string to Issue.State enum.
     * <p>
     * Handles both REST API (lowercase) and GraphQL API (uppercase) states.
     *
     * @param state the state string from GitHub API
     * @return the corresponding Issue.State enum value
     */
    public static Issue.State parseIssueState(String state) {
        if (state == null) {
            return Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "CLOSED", "MERGED" -> Issue.State.CLOSED;
            default -> Issue.State.OPEN;
        };
    }

    /**
     * Converts a GitHub API state reason string to Issue.StateReason enum.
     *
     * @param stateReason the state reason string from GitHub API
     * @return the corresponding Issue.StateReason enum value, or null
     */
    public static Issue.StateReason parseIssueStateReason(String stateReason) {
        if (stateReason == null) {
            return null;
        }
        return switch (stateReason.toUpperCase()) {
            case "COMPLETED" -> Issue.StateReason.COMPLETED;
            case "REOPENED" -> Issue.StateReason.REOPENED;
            case "NOT_PLANNED" -> Issue.StateReason.NOT_PLANNED;
            default -> null;
        };
    }

    /**
     * Converts a GitHub API milestone state string to Milestone.State enum.
     *
     * @param state the state string from GitHub API
     * @return the corresponding Milestone.State enum value
     */
    public static Milestone.State parseMilestoneStateEnum(String state) {
        if (state == null) {
            return Milestone.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "CLOSED" -> Milestone.State.CLOSED;
            default -> Milestone.State.OPEN;
        };
    }

    /**
     * Parses a user from a GraphQL map response.
     */
    @SuppressWarnings("unchecked")
    public static GitHubUserDTO parseUser(Map<String, Object> userData) {
        if (userData == null) {
            return null;
        }
        return new GitHubUserDTO(
            null,
            parseLong(userData.get("databaseId")),
            getString(userData, "login"),
            getString(userData, "avatarUrl"),
            null,
            getString(userData, "name"),
            getString(userData, "email")
        );
    }

    /**
     * Parses a list of users from a GraphQL connection response.
     */
    @SuppressWarnings("unchecked")
    public static List<GitHubUserDTO> parseUserList(Map<String, Object> connectionData) {
        List<GitHubUserDTO> users = new ArrayList<>();
        if (connectionData == null) {
            return users;
        }
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) connectionData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                GitHubUserDTO user = parseUser(node);
                if (user != null) {
                    users.add(user);
                }
            }
        }
        return users;
    }

    /**
     * Parses a list of labels from a GraphQL connection response.
     */
    @SuppressWarnings("unchecked")
    public static List<GitHubLabelDTO> parseLabelList(Map<String, Object> connectionData) {
        List<GitHubLabelDTO> labels = new ArrayList<>();
        if (connectionData == null) {
            return labels;
        }
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) connectionData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                labels.add(
                    new GitHubLabelDTO(
                        null,
                        getString(node, "id"),
                        getString(node, "name"),
                        getString(node, "description"),
                        getString(node, "color")
                    )
                );
            }
        }
        return labels;
    }

    /**
     * Parses a milestone from a GraphQL map response.
     */
    @SuppressWarnings("unchecked")
    public static GitHubMilestoneDTO parseMilestone(Map<String, Object> milestoneData) {
        if (milestoneData == null) {
            return null;
        }
        return new GitHubMilestoneDTO(
            null,
            parseIntOrDefault(milestoneData.get("number"), 0),
            getString(milestoneData, "title"),
            getString(milestoneData, "description"),
            convertMilestoneState(getString(milestoneData, "state")),
            parseInstant(milestoneData.get("dueOn")),
            getString(milestoneData, "url")
        );
    }

    /**
     * Parses requested reviewers from a GraphQL connection response.
     */
    @SuppressWarnings("unchecked")
    public static List<GitHubUserDTO> parseRequestedReviewers(Map<String, Object> reviewRequestsData) {
        List<GitHubUserDTO> reviewers = new ArrayList<>();
        if (reviewRequestsData == null) {
            return reviewers;
        }
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) reviewRequestsData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                Map<String, Object> requestedReviewer = (Map<String, Object>) node.get("requestedReviewer");
                if (requestedReviewer != null) {
                    GitHubUserDTO user = parseUser(requestedReviewer);
                    if (user != null) {
                        reviewers.add(user);
                    }
                }
            }
        }
        return reviewers;
    }
}
