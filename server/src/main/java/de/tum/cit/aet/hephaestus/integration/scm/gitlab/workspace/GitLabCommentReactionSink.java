package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGateway;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScmCommentReactionSink;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * {@link ScmCommentReactionSink} for GitLab — posts award-emoji reactions on MR notes via
 * the GitLab GraphQL {@code awardEmojiAdd} mutation.
 *
 * <p>Adapter around {@link GitLabGraphQlClientProvider} so cross-module callers (the bot
 * command processor in the agent module) don't have to import GitLab's GraphQL client.
 * Best-effort by contract: every remote call is wrapped and exceptions are swallowed.
 */
@Component
@OutboundEgressGateway
@ConditionalOnBean(GitLabGraphQlClientProvider.class)
@Slf4j
public class GitLabCommentReactionSink implements ScmCommentReactionSink {

    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(10);

    private final GitLabGraphQlClientProvider gitLabGraphQlProvider;
    private final OutboundEgressGuard egressGuard;

    public GitLabCommentReactionSink(
        GitLabGraphQlClientProvider gitLabGraphQlProvider,
        OutboundEgressGuard egressGuard
    ) {
        this.gitLabGraphQlProvider = gitLabGraphQlProvider;
        this.egressGuard = egressGuard;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void react(long scopeId, long commentNativeId, String reactionName) {
        try {
            String awardableId = "gid://gitlab/Note/" + commentNativeId;
            egressGuard.requireDeliveryAllowed("gitlab.react-to-comment");
            var response = gitLabGraphQlProvider
                .forScope(scopeId)
                .documentName("AwardEmojiAdd")
                .variable("awardableId", awardableId)
                .variable("name", reactionName)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response != null && response.isValid()) {
                log.debug("Added {} reaction: noteId={}, scopeId={}", reactionName, commentNativeId, scopeId);
            } else {
                log.debug(
                    "{} reaction GraphQL response invalid: noteId={}, errors={}",
                    reactionName,
                    commentNativeId,
                    response != null ? response.getErrors() : "null"
                );
            }
        } catch (OutboundEgressSuppressedException e) {
            log.debug(
                "Suppressed GitLab comment reaction while instance Silent Mode is engaged: reactionName={}, noteId={}, scopeId={}",
                reactionName,
                commentNativeId,
                scopeId
            );
        } catch (Exception e) {
            log.debug(
                "Failed to add {} reaction (non-fatal): noteId={}, scopeId={}, error={}",
                reactionName,
                commentNativeId,
                scopeId,
                e.getMessage()
            );
        }
    }
}
