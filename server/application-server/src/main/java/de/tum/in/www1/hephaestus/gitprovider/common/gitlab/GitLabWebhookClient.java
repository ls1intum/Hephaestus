package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Scope-based client for GitLab webhook CRUD operations.
 *
 * <p>Uses <b>GraphQL</b> for group lookup (reuses existing infrastructure:
 * {@link GitLabGraphQlClientProvider}, {@link GitLabGroupResponse},
 * {@link GitLabSyncConstants#extractNumericId}) and <b>REST</b> for webhook
 * management (no GraphQL mutations exist for webhooks).
 *
 * <p>All methods are scope-aware: they resolve the access token and server URL
 * for each workspace via {@link GitLabTokenService}.
 *
 * @see <a href="https://docs.gitlab.com/ee/api/group_level_webhooks.html">GitLab Group Webhooks API</a>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabWebhookClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabTokenService tokenService;
    private final WebClient webClient;

    public GitLabWebhookClient(
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabTokenService tokenService,
        WebClient.Builder webClientBuilder
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.tokenService = tokenService;
        this.webClient = webClientBuilder.build();
    }

    // ========================================================================
    // GraphQL: Group Lookup (reuses existing infra)
    // ========================================================================

    /**
     * Looks up a GitLab group by its full path and returns the stable numeric ID.
     *
     * @param scopeId   the workspace/scope ID for authentication
     * @param groupPath the full path of the group (e.g., {@code "org/team"})
     * @return group info including numeric ID
     * @throws IllegalStateException    if the group is not found
     * @throws IllegalArgumentException if the global ID format is invalid
     */
    public GroupInfo lookupGroup(Long scopeId, String groupPath) {
        GitLabGroupResponse group = graphQlClientProvider
            .forScope(scopeId)
            .documentName("GetGroup")
            .variable("fullPath", groupPath)
            .retrieve("group")
            .toEntity(GitLabGroupResponse.class)
            .block(REQUEST_TIMEOUT);

        if (group == null) {
            throw new IllegalStateException("GitLab group not found: path=" + groupPath + ", scopeId=" + scopeId);
        }

        long numericId = GitLabSyncConstants.extractNumericId(group.id());
        return new GroupInfo(numericId, group.name(), group.fullPath());
    }

    // ========================================================================
    // REST: Webhook CRUD (no GraphQL mutations available)
    // ========================================================================

    /**
     * Registers a group-level webhook.
     *
     * @param scopeId  the workspace/scope ID
     * @param groupId  the numeric group ID
     * @param config   webhook configuration
     * @return webhook info with the assigned ID
     * @throws WebClientResponseException on API errors (e.g., 403 Forbidden)
     */
    public WebhookInfo registerGroupWebhook(Long scopeId, long groupId, WebhookConfig config) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient
            .post()
            .uri(serverUrl + "/api/v4/groups/{groupId}/hooks", groupId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .bodyValue(config.toPayload())
            .retrieve()
            .bodyToMono(Map.class)
            .block(REQUEST_TIMEOUT);

        if (response == null) {
            throw new IllegalStateException("Empty response from GitLab webhook registration");
        }

        long webhookId = ((Number) response.get("id")).longValue();
        String url = (String) response.get("url");
        log.info("Registered GitLab group webhook: scopeId={}, groupId={}, webhookId={}", scopeId, groupId, webhookId);
        return new WebhookInfo(webhookId, url);
    }

    /**
     * Deregisters a group-level webhook. Silently succeeds if the webhook was already deleted (404).
     *
     * @param scopeId   the workspace/scope ID
     * @param groupId   the numeric group ID
     * @param webhookId the webhook ID to delete
     */
    public void deregisterGroupWebhook(Long scopeId, long groupId, long webhookId) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        try {
            webClient
                .delete()
                .uri(serverUrl + "/api/v4/groups/{groupId}/hooks/{hookId}", groupId, webhookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);

            log.info(
                "Deregistered GitLab group webhook: scopeId={}, groupId={}, webhookId={}",
                scopeId,
                groupId,
                webhookId
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info(
                    "GitLab webhook already deleted: scopeId={}, groupId={}, webhookId={}",
                    scopeId,
                    groupId,
                    webhookId
                );
            } else {
                throw e;
            }
        }
    }

    /**
     * Gets a specific group webhook by ID.
     *
     * @param scopeId   the workspace/scope ID
     * @param groupId   the numeric group ID
     * @param webhookId the webhook ID
     * @return the webhook info, or empty if not found (404)
     */
    public Optional<WebhookInfo> getGroupWebhook(Long scopeId, long groupId, long webhookId) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient
                .get()
                .uri(serverUrl + "/api/v4/groups/{groupId}/hooks/{hookId}", groupId, webhookId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block(REQUEST_TIMEOUT);

            if (response == null) {
                return Optional.empty();
            }

            long id = ((Number) response.get("id")).longValue();
            String url = (String) response.get("url");
            return Optional.of(new WebhookInfo(id, url));
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Lists all group-level webhooks.
     *
     * @param scopeId the workspace/scope ID
     * @param groupId the numeric group ID
     * @return list of webhook info
     */
    public List<WebhookInfo> listGroupWebhooks(Long scopeId, long groupId) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        List<Map<String, Object>> response = webClient
            .get()
            .uri(serverUrl + "/api/v4/groups/{groupId}/hooks", groupId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .block(REQUEST_TIMEOUT);

        if (response == null) {
            return List.of();
        }

        return response
            .stream()
            .map(hook -> new WebhookInfo(((Number) hook.get("id")).longValue(), (String) hook.get("url")))
            .toList();
    }

    /**
     * Checks whether a 403/404 response indicates insufficient permissions
     * (as opposed to a transient failure).
     */
    public static boolean isPermissionError(HttpStatusCode status) {
        return status.value() == 403 || status.value() == 404;
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record GroupInfo(long id, String name, String fullPath) {}

    public record WebhookInfo(long id, String url) {}

    /**
     * Webhook configuration payload matching the GitLab API contract.
     *
     * @see <a href="https://docs.gitlab.com/ee/api/group_level_webhooks.html#add-a-group-hook">Add Group Hook</a>
     */
    public record WebhookConfig(
        String url,
        String token,
        boolean mergeRequestsEvents,
        boolean issuesEvents,
        boolean noteEvents,
        boolean pushEvents,
        boolean pipelineEvents,
        boolean enableSslVerification
    ) {
        Map<String, Object> toPayload() {
            return Map.of(
                "url",
                url,
                "token",
                token,
                "merge_requests_events",
                mergeRequestsEvents,
                "issues_events",
                issuesEvents,
                "note_events",
                noteEvents,
                "push_events",
                pushEvents,
                "pipeline_events",
                pipelineEvents,
                "enable_ssl_verification",
                enableSslVerification
            );
        }
    }
}
