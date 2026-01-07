package de.tum.in.www1.hephaestus.gitprovider.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/**
 * How the author is associated with the repository.
 */
public enum AuthorAssociation {
    COLLABORATOR,
    CONTRIBUTOR,
    FIRST_TIMER,
    FIRST_TIME_CONTRIBUTOR,
    MANNEQUIN,
    MEMBER,
    NONE,
    OWNER;

    private static final Logger log = LoggerFactory.getLogger(AuthorAssociation.class);

    /**
     * Parse an author association from a string value.
     * Handles both webhook and GraphQL formats (e.g., "MEMBER", "member", "FIRST_TIME_CONTRIBUTOR").
     *
     * @param value the string value to parse
     * @return the matching AuthorAssociation, or NONE if null/unrecognized
     */
    public static AuthorAssociation fromString(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("Unknown author association: {}, defaulting to NONE", value);
            return NONE;
        }
    }
}
