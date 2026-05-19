package de.tum.in.www1.hephaestus.gitprovider.common.events;

import org.springframework.lang.Nullable;

/**
 * Published when a bot command (e.g., {@code /hephaestus review}) is detected
 * in a merge request comment. Listeners in the agent module handle execution.
 *
 * @param repositoryId the repository's DB id
 * @param mrNumber     the merge request iid (project-scoped number)
 * @param noteBody     the raw comment body
 * @param noteAuthor   the login of the note author
 * @param noteId       the GitLab note ID (for award emoji reactions); null for non-GitLab sources
 * @param projectPath  the GitLab project path with namespace (for REST API calls); null for GitHub
 * @param scopeId      the workspace scope ID (for credential resolution); null if unavailable
 */
public record BotCommandReceivedEvent(
    long repositoryId,
    int mrNumber,
    String noteBody,
    String noteAuthor,
    @Nullable Long noteId,
    @Nullable String projectPath,
    @Nullable Long scopeId
) {}
