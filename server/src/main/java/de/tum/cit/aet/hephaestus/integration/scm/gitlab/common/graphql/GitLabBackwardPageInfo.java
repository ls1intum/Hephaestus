package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * The backward half of the Relay {@code PageInfo} type, for GitLab connections walked with
 * {@code last}/{@code before}. {@link GitLabPageInfo} is the forward half.
 *
 * @param startCursor cursor of the returned page's first item; pass as {@code before} to continue
 *                    backwards (null when the connection is empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabBackwardPageInfo(boolean hasPreviousPage, @Nullable String startCursor) {}
