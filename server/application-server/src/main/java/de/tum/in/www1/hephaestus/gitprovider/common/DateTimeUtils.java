package de.tum.in.www1.hephaestus.gitprovider.common;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * Utility class for null-safe type conversions commonly used in GitHub DTO mappings.
 * <p>
 * These methods handle the conversion from GraphQL response types (OffsetDateTime, URI)
 * to domain model types (Instant, String) with proper null handling.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts a nullable OffsetDateTime to Instant.
     * <p>
     * This conversion is needed because:
     * - GraphQL generated models use OffsetDateTime
     * - Domain entities use Instant for timezone-agnostic timestamp storage
     *
     * @param dateTime the OffsetDateTime from GraphQL response (may be null)
     * @return the corresponding Instant, or null if input is null
     */
    @Nullable
    public static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }

    /**
     * Converts a nullable URI to its String representation.
     *
     * @param uri the URI (may be null)
     * @return the URI as a String, or null if input is null
     */
    @Nullable
    public static String uriToString(@Nullable URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
