package de.tum.in.www1.hephaestus.gitprovider.common;

/**
 * Utility class for sanitizing strings for PostgreSQL storage.
 */
public final class PostgresStringUtils {

    private PostgresStringUtils() {
        // Utility class
    }

    /**
     * Sanitizes a string for PostgreSQL storage by removing null bytes (0x00).
     * PostgreSQL's TEXT/VARCHAR types don't accept null bytes in UTF-8 encoded strings.
     *
     * @param input The string to sanitize, may be null
     * @return The sanitized string with null bytes removed, or null if input was null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Remove null bytes (0x00) which PostgreSQL rejects with "invalid byte sequence for encoding UTF8: 0x00"
        return input.replace("\u0000", "");
    }
}
