package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Posts a reaction (e.g. {@code eyes}) onto a code-review comment.
 *
 * <p>Used by agent-side bot command processing to acknowledge a {@code /hephaestus review}
 * command on a merge request comment without coupling the agent module to a specific
 * provider's GraphQL client.
 *
 * <p>One impl per SCM kind. Implementations are best-effort: a failed reaction must
 * never abort the surrounding workflow — wrap remote calls and swallow exceptions.
 */
public interface ScmCommentReactionSink {
    /** The SCM kind this reaction sink targets. */
    IntegrationKind kind();

    /**
     * Posts a reaction emoji onto a comment. Best-effort.
     *
     * @param scopeId          the workspace scope owning the credentials
     * @param commentNativeId  the provider-native comment id (e.g. GitLab note id, GitHub comment id)
     * @param reactionName     the reaction name (e.g. {@code "eyes"})
     */
    void react(long scopeId, long commentNativeId, String reactionName);
}
