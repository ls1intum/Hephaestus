package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Reusable pagination info for GitLab GraphQL cursor-based pagination.
 *
 * @param hasNextPage whether more pages are available
 * @param endCursor   opaque cursor for the next page (null on last page)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabPageInfo(boolean hasNextPage, @Nullable String endCursor) {}
