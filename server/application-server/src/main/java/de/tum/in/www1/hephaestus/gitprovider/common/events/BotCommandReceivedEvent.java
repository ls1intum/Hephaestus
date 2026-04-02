package de.tum.in.www1.hephaestus.gitprovider.common.events;

/**
 * Published when a bot command (e.g., {@code /hephaestus review}) is detected
 * in a merge request comment. Listeners in the agent module handle execution.
 *
 * @param repositoryId the repository's DB id
 * @param mrNumber     the merge request iid (project-scoped number)
 * @param noteBody     the raw comment body
 * @param noteAuthor   the login of the note author
 */
public record BotCommandReceivedEvent(long repositoryId, int mrNumber, String noteBody, String noteAuthor) {}
