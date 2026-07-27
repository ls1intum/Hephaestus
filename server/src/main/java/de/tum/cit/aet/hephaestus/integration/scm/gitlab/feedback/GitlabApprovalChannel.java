package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApprovalChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * GitLab adapter for {@link ApprovalChannel}: approves a merge request through the REST
 * {@code POST /projects/:id/merge_requests/:iid/approve} endpoint.
 *
 * <p>REST, not GraphQL, because GitLab has no approval mutation — the same reason
 * {@link de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabWebhookClient} uses REST for webhooks.
 * (This channel previously sent a {@code mergeRequestApprove} mutation, which GitLab's schema has never
 * defined; every approval failed with an undefined-field error.)
 *
 * <p>Gated on {@code hephaestus.integration.gitlab.enabled=true} to track {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitlabApprovalChannel implements ApprovalChannel {

    private static final Logger log = LoggerFactory.getLogger(GitlabApprovalChannel.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitLabTokenService tokenService;
    private final GitlabFeedbackChannel feedbackChannel;
    private final WebClient webClient;

    public GitlabApprovalChannel(
        GitLabGraphQlClientProvider gitLabProvider,
        GitLabTokenService tokenService,
        GitlabFeedbackChannel feedbackChannel,
        WebClient.Builder webClientBuilder
    ) {
        this.gitLabProvider = gitLabProvider;
        this.tokenService = tokenService;
        this.feedbackChannel = feedbackChannel;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void approve(FeedbackChannel.FeedbackTarget target, String message) {
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException("GitLab rate limit critical — skipping approval for scope " + scopeId);
        }

        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        try {
            webClient
                .post()
                .uri(approvalUri(tokenService.resolveServerUrl(scopeId), mr))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken(scopeId))
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException e) {
            // GitLab answers 401 when the token's user may not approve this MR at all — most often because it
            // authored it. Naming that keeps a permanent, unretryable condition from reading as a flaky call.
            throw new FeedbackDeliveryException(
                "GitLab approve failed for " +
                    mr.projectPath() +
                    "!" +
                    mr.iid() +
                    ": HTTP " +
                    e.getStatusCode() +
                    (e.getStatusCode().value() == 401 ? " (the token's user may not approve this merge request)" : ""),
                e
            );
        } catch (RuntimeException e) {
            throw new FeedbackDeliveryException("GitLab approve transport error: " + e.getMessage(), e);
        }
        log.info("Approved GitLab MR: workspaceId={}, mr={}!{}", scopeId, mr.projectPath(), mr.iid());

        // GitLab's approve endpoint accepts no body — post the message as a separate note.
        if (message != null && !message.isBlank()) {
            feedbackChannel.postSummary(target, new FeedbackChannel.FeedbackContent(message, ""));
        }
    }

    /**
     * Builds the approval endpoint as a ready-made {@link URI} so no URI-template expansion can re-encode the
     * {@code %2F} separators: GitLab addresses a project by its full path only in percent-encoded form, and a
     * double-encoded {@code %252F} resolves to no project at all. Project and namespace paths are restricted
     * to unreserved characters, so escaping the separators is the whole encoding.
     */
    private static URI approvalUri(String serverUrl, MrCoordinates mr) {
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        return URI.create(
            base +
                "/api/v4/projects/" +
                mr.projectPath().replace("/", "%2F") +
                "/merge_requests/" +
                mr.iid() +
                "/approve"
        );
    }
}
