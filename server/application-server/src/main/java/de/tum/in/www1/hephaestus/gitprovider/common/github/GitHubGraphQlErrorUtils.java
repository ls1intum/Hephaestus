package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.List;
import java.util.Map;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;

/**
 * Utility class for analyzing GitHub GraphQL API errors.
 * <p>
 * GitHub's GraphQL API returns structured errors with an extensions field that
 * contains error classification. This utility helps detect specific error types
 * like NOT_FOUND to enable appropriate handling (e.g., soft-deleting stale data
 * rather than logging errors for expected scenarios).
 */
public final class GitHubGraphQlErrorUtils {

    private GitHubGraphQlErrorUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * GitHub GraphQL error type indicating a resource was not found.
     * <p>
     * This occurs when querying for issues, pull requests, or other resources
     * that have been deleted from GitHub but may still exist in our database.
     */
    public static final String ERROR_TYPE_NOT_FOUND = "NOT_FOUND";

    /**
     * GitHub GraphQL error type indicating forbidden access.
     */
    public static final String ERROR_TYPE_FORBIDDEN = "FORBIDDEN";

    /**
     * Checks if the GraphQL response contains a NOT_FOUND error for the specified path.
     * <p>
     * GitHub returns NOT_FOUND errors when querying for resources that don't exist,
     * such as deleted issues or pull requests. This is distinct from null data,
     * which indicates no results but not an error condition.
     *
     * @param response the GraphQL response to check
     * @param pathPrefix the path prefix to check for (e.g., "repository.issue" or "repository.pullRequest")
     * @return true if the response contains a NOT_FOUND error at the specified path
     */
    public static boolean isNotFoundError(@Nullable ClientGraphQlResponse response, String pathPrefix) {
        return hasErrorType(response, ERROR_TYPE_NOT_FOUND, pathPrefix);
    }

    /**
     * Checks if the GraphQL response contains any NOT_FOUND error.
     *
     * @param response the GraphQL response to check
     * @return true if the response contains any NOT_FOUND error
     */
    public static boolean hasAnyNotFoundError(@Nullable ClientGraphQlResponse response) {
        return hasErrorType(response, ERROR_TYPE_NOT_FOUND, null);
    }

    /**
     * Checks if the GraphQL response contains an error of the specified type.
     *
     * @param response the GraphQL response to check
     * @param errorType the error type to look for (e.g., "NOT_FOUND", "FORBIDDEN")
     * @param pathPrefix optional path prefix to filter errors (null to check all errors)
     * @return true if a matching error is found
     */
    public static boolean hasErrorType(
        @Nullable ClientGraphQlResponse response,
        String errorType,
        @Nullable String pathPrefix
    ) {
        if (response == null) {
            return false;
        }

        List<ResponseError> errors = response.getErrors();
        if (errors == null || errors.isEmpty()) {
            return false;
        }

        for (ResponseError error : errors) {
            if (matchesErrorType(error, errorType) && matchesPath(error, pathPrefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the error has the specified type in its extensions.
     */
    private static boolean matchesErrorType(ResponseError error, String errorType) {
        Map<String, Object> extensions = error.getExtensions();
        if (extensions == null) {
            return false;
        }

        Object type = extensions.get("type");
        return errorType.equals(type);
    }

    /**
     * Checks if the error path matches or is a child of the specified prefix.
     * <p>
     * The path is a list of path segments (strings and integers for array indices).
     * For example, ["repository", "issue"] would match pathPrefix "repository.issue",
     * and ["repository", "issue", "comments"] would also match "repository.issue".
     * However, ["repository"] would NOT match "repository.issue".
     *
     * @param error      the error to check
     * @param pathPrefix the path prefix to match against (must not be null)
     * @return true if the error path starts with the given prefix
     */
    private static boolean matchesPath(ResponseError error, @Nullable String pathPrefix) {
        if (pathPrefix == null) {
            return true;
        }

        // Use getParsedPath() which returns List<Object> with field names (Strings) and array indices (Integers)
        List<Object> path = error.getParsedPath();
        if (path == null || path.isEmpty()) {
            // Fallback: check the string path representation
            String stringPath = error.getPath();
            if (stringPath == null || stringPath.isEmpty()) {
                return false;
            }
            // Only match if the error path is at or under the expected location
            return stringPath.startsWith(pathPrefix);
        }

        // Build the path string from the path segments (excluding array indices)
        StringBuilder pathBuilder = new StringBuilder();
        for (Object segment : path) {
            if (segment instanceof String stringSegment) {
                if (pathBuilder.length() > 0) {
                    pathBuilder.append(".");
                }
                pathBuilder.append(stringSegment);
            }
            // Skip integer indices (array positions)
        }

        String actualPath = pathBuilder.toString();
        // Only match if the error path is at or under the expected location
        return actualPath.startsWith(pathPrefix);
    }

    /**
     * Extracts a human-readable error message for NOT_FOUND errors.
     * <p>
     * Useful for logging at an appropriate level (WARN instead of ERROR).
     *
     * @param response the GraphQL response
     * @return a descriptive message about the NOT_FOUND error, or null if no such error
     */
    @Nullable
    public static String getNotFoundErrorMessage(@Nullable ClientGraphQlResponse response) {
        if (response == null) {
            return null;
        }

        List<ResponseError> errors = response.getErrors();
        if (errors == null) {
            return null;
        }

        for (ResponseError error : errors) {
            if (matchesErrorType(error, ERROR_TYPE_NOT_FOUND)) {
                return error.getMessage();
            }
        }

        return null;
    }
}
