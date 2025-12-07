package de.tum.in.www1.hephaestus.core;

/**
 * Utility class for safe logging operations.
 * Provides methods to sanitize user-controlled input before logging to prevent log injection attacks.
 */
public final class LoggingUtils {

    private LoggingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes a string for safe logging by removing control characters that could be used
     * for log injection attacks (newlines, carriage returns, tabs, and other control characters).
     *
     * <p>This method should be used whenever logging user-controlled input such as:
     * <ul>
     *   <li>Repository names, slugs, or identifiers</li>
     *   <li>User-provided strings (labels, descriptions, etc.)</li>
     *   <li>GitHub account logins</li>
     *   <li>Exception messages from external sources</li>
     * </ul>
     *
     * @param input the string to sanitize (may be null)
     * @return the sanitized string with control characters replaced by underscores,
     *         or null if input was null
     */
    public static String sanitizeForLog(String input) {
        if (input == null) {
            return null;
        }
        return input
            .replaceAll("[\\r\\n\\t\\u0000-\\u001F\\u007F\\u0085\\u2028\\u2029]", "_")
            .replaceAll("\\e\\[[0-9;]*[A-Za-z]", "_");
    }
}
