package de.tum.cit.aet.hephaestus.integration.core.events;

/**
 * Published when a bot command (e.g. {@code /hephaestus review}) is detected on a
 * pull-request / merge-request comment. Subscribers (e.g. {@code BotCommandProcessor}
 * in {@code agent/}) handle execution and may react to the comment via the
 * {@code ScmCommentReactionSink} SPI keyed by {@code IntegrationKind}.
 *
 * <p>Vendor-neutral by contract — all fields are shape-only (numeric IDs, strings)
 * with no vendor-specific semantics in their names. Today only the GitLab webhook
 * path publishes this event; a future GitHub/Bitbucket publisher fills the same
 * record and the agent module dispatches by {@link
 * de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind} of the underlying
 * repository.
 *
 * @param repositoryId the repository's DB id (resolves the integration kind for
 *                     reaction dispatch via the repo's connection)
 * @param mrNumber     the PR / MR number (project-scoped)
 * @param noteBody     the raw comment body
 * @param noteAuthor   the login of the comment author
 * @param commentId    the comment's native id (vendor-shape Long), used by reaction
 *                     SPI implementations to add an emoji reaction
 * @param scopeId      the workspace scope ID, for credential resolution; null when
 *                     the publish path could not resolve a scope
 */
public record BotCommandReceivedEvent(
    long repositoryId,
    int mrNumber,
    String noteBody,
    String noteAuthor,
    Long commentId,
    Long scopeId
) {}
