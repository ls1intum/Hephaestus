package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookConfig;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Orchestrates GitLab webhook auto-registration and PAT rotation for workspaces.
 *
 * <p>This service is the workspace-side orchestrator that coordinates:
 * <ol>
 *   <li><b>Token rotation:</b> Checks PAT expiry and rotates before it expires</li>
 *   <li><b>Webhook registration:</b> Registers group-level webhooks idempotently</li>
 *   <li><b>Webhook deregistration:</b> Best-effort cleanup during workspace purge</li>
 * </ol>
 *
 * <p>All GitLab API calls are delegated to clients in {@code gitprovider.common.gitlab}.
 * This service handles idempotency, entity updates, and error handling.
 *
 * <p>Dependencies are injected via {@link ObjectProvider} to gracefully handle cases
 * where GitLab integration is disabled ({@code hephaestus.gitlab.enabled=false}).
 */
@Service
public class GitLabWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookService.class);

    private final ObjectProvider<GitLabWebhookClient> webhookClientProvider;
    private final ObjectProvider<GitLabTokenRotationClient> rotationClientProvider;
    private final ObjectProvider<GitLabTokenService> tokenServiceProvider;
    private final WebhookProperties webhookProperties;
    private final WorkspaceRepository workspaceRepository;

    public GitLabWebhookService(
        ObjectProvider<GitLabWebhookClient> webhookClientProvider,
        ObjectProvider<GitLabTokenRotationClient> rotationClientProvider,
        ObjectProvider<GitLabTokenService> tokenServiceProvider,
        WebhookProperties webhookProperties,
        WorkspaceRepository workspaceRepository
    ) {
        this.webhookClientProvider = webhookClientProvider;
        this.rotationClientProvider = rotationClientProvider;
        this.tokenServiceProvider = tokenServiceProvider;
        this.webhookProperties = webhookProperties;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Checks if the workspace's PAT is expiring soon and rotates it if needed.
     *
     * <p>Rotation happens <em>before</em> webhook registration and sync to ensure
     * all subsequent API calls use a fresh token. The old token is immediately
     * revoked by GitLab, so the new token must be persisted right away.
     *
     * @param workspace the workspace to check
     */
    @Transactional
    public void rotateTokenIfNeeded(Workspace workspace) {
        if (workspace.getProviderType() != GitProviderType.GITLAB) {
            return;
        }

        var rotationClient = rotationClientProvider.getIfAvailable();
        if (rotationClient == null) {
            log.debug("Token rotation skipped: rotation client unavailable, workspaceId={}", workspace.getId());
            return;
        }

        int thresholdDays = webhookProperties.tokenRotationThresholdDays();
        if (thresholdDays <= 0) {
            return;
        }

        try {
            var tokenInfo = rotationClient.getTokenInfo(workspace.getId());
            if (tokenInfo.expiresAt() == null) {
                log.debug("Token has no expiry, rotation not needed: workspaceId={}", workspace.getId());
                return;
            }

            LocalDate threshold = LocalDate.now().plusDays(thresholdDays);
            if (tokenInfo.expiresAt().isAfter(threshold)) {
                log.debug(
                    "Token not expiring soon: workspaceId={}, expiresAt={}, threshold={}",
                    workspace.getId(),
                    tokenInfo.expiresAt(),
                    threshold
                );
                return;
            }

            LocalDate newExpiry = LocalDate.now().plusDays(webhookProperties.tokenRotationValidityDays());
            var rotatedToken = rotationClient.rotateToken(workspace.getId(), newExpiry);

            // Critical: persist new token immediately — old token is already revoked
            workspace.setPersonalAccessToken(rotatedToken.token());
            workspaceRepository.save(workspace);

            // Invalidate token cache so subsequent calls use the new token
            var tokenService = tokenServiceProvider.getIfAvailable();
            if (tokenService != null) {
                tokenService.invalidateCache(workspace.getId());
            }

            log.info(
                "Rotated GitLab PAT: workspaceId={}, oldExpiry={}, newExpiry={}",
                workspace.getId(),
                tokenInfo.expiresAt(),
                rotatedToken.expiresAt()
            );
        } catch (WebClientResponseException | IllegalStateException e) {
            // Token rotation failure is non-fatal — the token still works until actual expiry
            log.warn("Token rotation failed: workspaceId={}, error={}", workspace.getId(), e.getMessage());
        }
    }

    /**
     * Registers a group-level webhook for the workspace, idempotently.
     *
     * <p>Strategy:
     * <ol>
     *   <li>If webhook ID is already stored, verify it still exists on GitLab</li>
     *   <li>Look up group by path to get numeric ID (GraphQL)</li>
     *   <li>Check if a webhook with our URL already exists (adopt if found)</li>
     *   <li>Register new webhook via REST</li>
     * </ol>
     *
     * @param workspace the workspace to register a webhook for
     * @return result indicating success or failure with reason
     */
    @Transactional
    public WebhookSetupResult registerWebhook(Workspace workspace) {
        if (workspace.getProviderType() != GitProviderType.GITLAB) {
            return WebhookSetupResult.skipped("Not a GitLab workspace");
        }

        if (!webhookProperties.isConfigured()) {
            return WebhookSetupResult.skipped("Webhook properties not configured (missing external URL or secret)");
        }

        var client = webhookClientProvider.getIfAvailable();
        if (client == null) {
            return WebhookSetupResult.skipped("GitLab webhook client unavailable (gitlab.enabled=false)");
        }

        Long scopeId = workspace.getId();
        String baseUrl = webhookProperties.externalUrl().replaceAll("/+$", "");
        String webhookUrl = baseUrl + "/gitlab";

        try {
            // Step 1: If we already have a webhook ID, verify it still exists
            if (workspace.getGitlabWebhookId() != null && workspace.getGitlabGroupId() != null) {
                Optional<WebhookInfo> existing = client.getGroupWebhook(
                    scopeId,
                    workspace.getGitlabGroupId(),
                    workspace.getGitlabWebhookId()
                );
                if (existing.isPresent()) {
                    log.debug(
                        "Webhook already registered: workspaceId={}, webhookId={}",
                        scopeId,
                        workspace.getGitlabWebhookId()
                    );
                    return WebhookSetupResult.success(workspace.getGitlabWebhookId(), workspace.getGitlabGroupId());
                }
                // Webhook was deleted externally — clear and re-register
                log.info(
                    "Stored webhook no longer exists on GitLab, re-registering: workspaceId={}, webhookId={}",
                    scopeId,
                    workspace.getGitlabWebhookId()
                );
                workspace.setGitlabWebhookId(null);
            }

            // Step 2: Look up group by path to get numeric ID
            long groupId;
            if (workspace.getGitlabGroupId() != null) {
                groupId = workspace.getGitlabGroupId();
            } else {
                var groupInfo = client.lookupGroup(scopeId, workspace.getAccountLogin());
                groupId = groupInfo.id();
                workspace.setGitlabGroupId(groupId);
            }

            // Step 3: Check if a webhook with our URL already exists (adopt it)
            List<WebhookInfo> existingHooks = client.listGroupWebhooks(scopeId, groupId);
            Optional<WebhookInfo> matchingHook = existingHooks
                .stream()
                .filter(hook -> webhookUrl.equals(hook.url()))
                .findFirst();

            if (matchingHook.isPresent()) {
                long adoptedId = matchingHook.get().id();
                workspace.setGitlabWebhookId(adoptedId);
                workspaceRepository.save(workspace);
                log.info(
                    "Adopted existing webhook: workspaceId={}, groupId={}, webhookId={}",
                    scopeId,
                    groupId,
                    adoptedId
                );
                return WebhookSetupResult.success(adoptedId, groupId);
            }

            // Step 4: Register new webhook
            WebhookConfig config = new WebhookConfig(
                webhookUrl,
                webhookProperties.secret(),
                true, // merge_requests_events
                true, // issues_events
                true, // note_events
                true, // push_events
                false, // pipeline_events
                true // enable_ssl_verification
            );

            WebhookInfo registered = client.registerGroupWebhook(scopeId, groupId, config);
            workspace.setGitlabWebhookId(registered.id());
            workspaceRepository.save(workspace);

            log.info(
                "Registered new webhook: workspaceId={}, groupId={}, webhookId={}",
                scopeId,
                groupId,
                registered.id()
            );
            return WebhookSetupResult.success(registered.id(), groupId);
        } catch (WebClientResponseException e) {
            if (GitLabWebhookClient.isPermissionOrNotFoundError(e.getStatusCode())) {
                String reason = String.format(
                    "Insufficient permissions (HTTP %d). Requires Owner role on GitLab group '%s' with Premium tier.",
                    e.getStatusCode().value(),
                    workspace.getAccountLogin()
                );
                log.info("Webhook registration failed: workspaceId={}, reason={}", scopeId, reason);
                // Still save groupId if we resolved it
                workspaceRepository.save(workspace);
                return WebhookSetupResult.failed(reason);
            }
            int status = e.getStatusCode().value();
            String apiReason = String.format("GitLab API error: %d", status);
            log.warn(
                "Webhook registration failed: workspaceId={}, reason={}, body={}",
                scopeId,
                apiReason,
                e.getResponseBodyAsString()
            );
            workspaceRepository.save(workspace);
            return WebhookSetupResult.failed(apiReason);
        }
    }

    /**
     * Deregisters the workspace's webhook from GitLab. Best-effort: never throws.
     *
     * @param workspace the workspace whose webhook to remove
     */
    @Transactional
    public void deregisterWebhook(Workspace workspace) {
        if (workspace.getGitlabWebhookId() == null || workspace.getGitlabGroupId() == null) {
            return;
        }

        var client = webhookClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("Webhook deregistration skipped: client unavailable, workspaceId={}", workspace.getId());
            clearWebhookFields(workspace);
            return;
        }

        try {
            client.deregisterGroupWebhook(
                workspace.getId(),
                workspace.getGitlabGroupId(),
                workspace.getGitlabWebhookId()
            );
        } catch (Exception e) {
            // Best-effort: log and continue. 401/403 = GitLab auto-disables failing webhooks.
            log.warn(
                "Webhook deregistration failed (best-effort): workspaceId={}, webhookId={}, error={}",
                workspace.getId(),
                workspace.getGitlabWebhookId(),
                e.getMessage()
            );
        }

        clearWebhookFields(workspace);
    }

    /**
     * Deregisters webhook by workspace ID. Used by purge contributors.
     *
     * <p>Uses {@link Propagation#NOT_SUPPORTED} to suspend the outer purge
     * transaction during the external HTTP call to GitLab. Running HTTP calls
     * inside a transaction is an anti-pattern (ties up a DB connection for the
     * duration of the network round-trip).
     *
     * @param workspaceId the workspace ID
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deregisterWebhookByWorkspaceId(Long workspaceId) {
        workspaceRepository.findById(workspaceId).ifPresent(this::deregisterWebhook);
    }

    private void clearWebhookFields(Workspace workspace) {
        workspace.setGitlabWebhookId(null);
        workspace.setGitlabGroupId(null);
        workspaceRepository.save(workspace);
    }
}
