package de.tum.in.www1.hephaestus.gitprovider.common;

import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

/**
 * Base converter for GitHub API objects to internal entities.
 * <p>
 * <strong>IMPORTANT:</strong> For GHUser objects, the standard getter methods like
 * {@code getCreatedAt()} and {@code getUpdatedAt()} trigger lazy-loading API calls
 * via {@code populate()}. These calls fail with 401 when using GitHub App installation
 * tokens because they don't have scope to access {@code /users/{login}}.
 * <p>
 * For GHUser, this converter skips createdAt/updatedAt - those fields will be
 * populated later via {@code GitHubUserSyncService} when the user is fully fetched.
 */
@ReadingConverter
public abstract class BaseGitServiceEntityConverter<S extends GHObject, T extends BaseGitServiceEntity>
    implements Converter<S, T> {

    private static final Logger logger = LoggerFactory.getLogger(BaseGitServiceEntityConverter.class);

    public abstract T update(@NonNull S source, @NonNull T target);

    /**
     * Sanitizes a string for PostgreSQL storage by removing null bytes (0x00).
     * PostgreSQL's TEXT/VARCHAR types don't accept null bytes in UTF-8 encoded strings.
     *
     * @param input The string to sanitize, may be null
     * @return The sanitized string with null bytes removed, or null if input was null
     */
    protected String sanitizeForPostgres(String input) {
        if (input == null) {
            return null;
        }
        // Remove null bytes (0x00) which PostgreSQL rejects with "invalid byte sequence for encoding UTF8: 0x00"
        return input.replace("\u0000", "");
    }

    protected void convertBaseFields(S source, T target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target must not be null");
        }

        target.setId(source.getId());

        // For GHUser, skip createdAt/updatedAt to avoid triggering populate()
        // These fields will be populated via GitHubUserSyncService.processUser()
        // when we have a fully-fetched user object
        if (source instanceof GHUser) {
            logger.trace("Skipping createdAt/updatedAt for GHUser {} to avoid populate() API call", source.getId());
            return;
        }

        // For other GHObject types (repositories, issues, PRs, etc.), use standard getters
        // These don't have the populate() lazy-loading issue
        try {
            target.setCreatedAt(source.getCreatedAt());
        } catch (Exception e) {
            logger.debug("Could not read createdAt for {}: {}", source.getClass().getSimpleName(), e.getMessage());
        }

        try {
            target.setUpdatedAt(source.getUpdatedAt());
        } catch (Exception e) {
            logger.debug("Could not read updatedAt for {}: {}", source.getClass().getSimpleName(), e.getMessage());
        }
    }
}
