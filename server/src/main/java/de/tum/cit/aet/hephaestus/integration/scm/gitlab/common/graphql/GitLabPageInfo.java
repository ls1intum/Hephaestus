package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Reusable pagination info for GitLab GraphQL cursor-based pagination.
 *
 * @param hasNextPage whether more pages are available
 * @param endCursor   opaque cursor for the next page (null on last page)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabPageInfo(boolean hasNextPage, @Nullable String endCursor) {}
