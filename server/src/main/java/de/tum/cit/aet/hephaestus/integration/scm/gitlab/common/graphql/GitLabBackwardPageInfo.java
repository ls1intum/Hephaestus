package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Pagination info for GitLab connections walked <em>backwards</em> with {@code last}/{@code before}.
 *
 * <p>The Relay {@code PageInfo} type has a forward half ({@code hasNextPage}/{@code endCursor}, see
 * {@link GitLabPageInfo}) and a backward half. They are separate records rather than one four-component
 * record because every sync service walks forwards and would otherwise carry two permanently-false
 * fields — the direction is part of what the type says.
 *
 * @param hasPreviousPage whether older items remain before the returned page
 * @param startCursor     opaque cursor of the returned page's first item; pass as {@code before} to
 *                        continue backwards (null when the connection is empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabBackwardPageInfo(boolean hasPreviousPage, @Nullable String startCursor) {}
