package de.tum.cit.aet.hephaestus.integration.core.events;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * Published when a bot command (e.g. {@code /hephaestus review}) is detected on a
 * pull-request / merge-request comment. Subscribers (e.g. {@code BotCommandProcessor}
 * in {@code agent/}) handle execution and may react to the comment via the
 * {@code ScmCommentReactionSink} SPI keyed by {@code IntegrationKind}.
 *
 * <p>Vendor-neutral by contract — the agent subscriber never names a concrete kind.
 * The publishing vendor adapter (which inherently knows its own kind) stamps {@code kind}
 * so the agent can dispatch the reaction through the SPI registry without hardcoding a
 * constant. Today only the GitLab webhook path publishes this event; a future
 * GitHub/Bitbucket publisher fills the same record with its own {@link IntegrationKind}.
 *
 * @param kind         the integration kind of the publishing SCM, used by the agent to
 *                     dispatch the comment reaction through the {@code ScmCommentReactionSink}
 *                     SPI registry without hardcoding a vendor constant
 * @param repositoryId the repository's DB id
 * @param mrNumber     the PR / MR number (project-scoped)
 * @param noteBody     the raw comment body
 * @param noteAuthor   the login of the comment author
 * @param commentId    the comment's native id (vendor-shape Long), used by reaction
 *                     SPI implementations to add an emoji reaction
 * @param scopeId      the workspace scope ID, for credential resolution; null when
 *                     the publish path could not resolve a scope
 */
public record BotCommandReceivedEvent(
    IntegrationKind kind,
    long repositoryId,
    int mrNumber,
    String noteBody,
    String noteAuthor,
    Long commentId,
    Long scopeId
) {}
