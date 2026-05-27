package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.GitLabConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabTokenRotationClient;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabWebhookClient;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabWebhookClient.WebhookConfig;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabWebhookClient.WebhookInfo;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
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
 * <p>All GitLab API calls are delegated to clients in {@code integration.scm.common.gitlab}.
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
    private final ConnectionService connectionService;

    public GitLabWebhookService(
        ObjectProvider<GitLabWebhookClient> webhookClientProvider,
        ObjectProvider<GitLabTokenRotationClient> rotationClientProvider,
        ObjectProvider<GitLabTokenService> tokenServiceProvider,
        WebhookProperties webhookProperties,
        WorkspaceRepository workspaceRepository,
        ConnectionService connectionService
    ) {
        this.webhookClientProvider = webhookClientProvider;
        this.rotationClientProvider = rotationClientProvider;
        this.tokenServiceProvider = tokenServiceProvider;
        this.webhookProperties = webhookProperties;
        this.workspaceRepository = workspaceRepository;
        this.connectionService = connectionService;
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
        if (!isGitLabWorkspace(workspace)) {
            return;
        }

        var rotationClient = rotationClientProvider.getIfAvailable();
        if (rotationClient == null) {
            log.debug("Token rotation skipped: rotation client unavailable, workspaceId={}", workspace.getId());
            return;
        }

        int thresholdDays = webhookProperties.tokenRotation().thresholdDays();
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

            LocalDate newExpiry = LocalDate.now().plusDays(webhookProperties.tokenRotation().validityDays());
            var rotatedToken = rotationClient.rotateToken(workspace.getId(), newExpiry);

            // Critical: persist new token immediately — old token is already revoked.
            // The token lives on the GitLab Connection's credential blob; rotateBearerToken
            // re-encrypts with the per-row AAD so cross-row substitution is prevented.
            connectionService.rotateBearerToken(
                workspace.getId(),
                IntegrationKind.GITLAB,
                new BearerToken(rotatedToken.token(), null)
            );

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

    private boolean isGitLabWorkspace(Workspace workspace) {
        return connectionService
            .findActiveProviderKind(workspace.getId())
            .map(k -> k == IntegrationKind.GITLAB)
            .orElse(false);
    }

    private Optional<GitLabConfig> gitLabConfig(Workspace workspace) {
        return connectionService.findActiveGitLabConfig(workspace.getId());
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
        Optional<GitLabConfig> configOpt = gitLabConfig(workspace);
        if (configOpt.isEmpty()) {
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
        String webhookUrl = baseUrl + "/webhooks/gitlab";

        GitLabConfig config = configOpt.get();
        Long currentWebhookId = config.gitlabWebhookId();
        Long currentGroupId = config.gitlabGroupId();

        try {
            // Step 1: If we already have a webhook ID, verify it still exists
            if (currentWebhookId != null && currentGroupId != null) {
                Optional<WebhookInfo> existing = client.getGroupWebhook(scopeId, currentGroupId, currentWebhookId);
                if (existing.isPresent()) {
                    log.debug("Webhook already registered: workspaceId={}, webhookId={}", scopeId, currentWebhookId);
                    return WebhookSetupResult.success(currentWebhookId, currentGroupId);
                }
                // Webhook was deleted externally — clear local id and re-register
                log.info(
                    "Stored webhook no longer exists on GitLab, re-registering: workspaceId={}, webhookId={}",
                    scopeId,
                    currentWebhookId
                );
                updateGitLabConfig(scopeId, cfg -> cfg.withGitlabWebhookId(null));
                currentWebhookId = null;
            }

            // Step 2: Look up group by path to get numeric ID
            long groupId;
            if (currentGroupId != null) {
                groupId = currentGroupId;
            } else {
                var groupInfo = client.lookupGroup(scopeId, workspace.getAccountLogin());
                groupId = groupInfo.id();
                long resolvedGroupId = groupId;
                updateGitLabConfig(scopeId, cfg -> cfg.withGitlabGroupId(resolvedGroupId));
            }

            // Step 3: Check if a webhook with our URL already exists (adopt it)
            List<WebhookInfo> existingHooks = client.listGroupWebhooks(scopeId, groupId);
            Optional<WebhookInfo> matchingHook = existingHooks
                .stream()
                .filter(hook -> webhookUrl.equals(hook.url()))
                .findFirst();

            if (matchingHook.isPresent()) {
                long adoptedId = matchingHook.get().id();
                updateGitLabConfig(scopeId, cfg -> cfg.withGitlabWebhookId(adoptedId));
                log.info(
                    "Adopted existing webhook: workspaceId={}, groupId={}, webhookId={}",
                    scopeId,
                    groupId,
                    adoptedId
                );
                return WebhookSetupResult.success(adoptedId, groupId);
            }

            // Step 4: Register new webhook
            WebhookConfig webhookConfig = new WebhookConfig(
                webhookUrl,
                webhookProperties.secret(),
                true, // merge_requests_events
                true, // issues_events
                true, // confidential_issues_events
                true, // note_events
                true, // confidential_note_events
                true, // push_events
                true, // tag_push_events
                false, // pipeline_events
                true, // milestone_events
                true, // member_events
                true, // subgroup_events
                true, // project_events
                true // enable_ssl_verification
            );

            WebhookInfo registered = client.registerGroupWebhook(scopeId, groupId, webhookConfig);
            updateGitLabConfig(scopeId, cfg -> cfg.withGitlabWebhookId(registered.id()));

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
            return WebhookSetupResult.failed(apiReason);
        }
    }

    /**
     * Mutates the workspace's GitLab Connection config via {@link ConnectionService#updateConfig}.
     * The cast inside the mutator is safe because we only reach this helper for workspaces
     * whose active SCM connection is GitLab (caller-side filtered by {@link #gitLabConfig}).
     */
    private void updateGitLabConfig(long workspaceId, java.util.function.UnaryOperator<GitLabConfig> mutator) {
        connectionService.updateConfig(workspaceId, IntegrationKind.GITLAB, cfg -> {
            if (!(cfg instanceof GitLabConfig gitLabCfg)) {
                throw new IllegalStateException(
                    "Expected GitLabConfig on workspace=" + workspaceId + " but got " + cfg.getClass().getSimpleName()
                );
            }
            return mutator.apply(gitLabCfg);
        });
    }

    /**
     * Deregisters the workspace's webhook from GitLab. Best-effort: never throws.
     *
     * @param workspace the workspace whose webhook to remove
     */
    @Transactional
    public void deregisterWebhook(Workspace workspace) {
        Optional<GitLabConfig> configOpt = gitLabConfig(workspace);
        if (configOpt.isEmpty()) {
            return;
        }
        GitLabConfig config = configOpt.get();
        if (config.gitlabWebhookId() == null || config.gitlabGroupId() == null) {
            return;
        }

        var client = webhookClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("Webhook deregistration skipped: client unavailable, workspaceId={}", workspace.getId());
            clearWebhookFields(workspace);
            return;
        }

        try {
            client.deregisterGroupWebhook(workspace.getId(), config.gitlabGroupId(), config.gitlabWebhookId());
        } catch (Exception e) {
            // Best-effort: log and continue. 401/403 = GitLab auto-disables failing webhooks.
            log.warn(
                "Webhook deregistration failed (best-effort): workspaceId={}, webhookId={}, error={}",
                workspace.getId(),
                config.gitlabWebhookId(),
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

    /**
     * Periodic health check for GitLab webhooks.
     * <p>
     * GitLab auto-disables webhooks after 40 consecutive failures. This scheduled
     * method verifies that registered webhooks still exist and re-registers them
     * if they were deleted or disabled externally.
     * <p>
     * Runs every 6 hours. Non-fatal: errors are logged but don't affect other operations.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void checkWebhookHealth() {
        var client = webhookClientProvider.getIfAvailable();
        if (client == null || !webhookProperties.isConfigured()) {
            return;
        }

        record GitLabHealthCandidate(Workspace workspace, Long groupId, Long webhookId) {}

        List<GitLabHealthCandidate> gitLabWorkspaces = workspaceRepository
            .findByStatus(Workspace.WorkspaceStatus.ACTIVE)
            .stream()
            .map(ws -> {
                Optional<GitLabConfig> cfg = gitLabConfig(ws);
                if (cfg.isEmpty() || cfg.get().gitlabWebhookId() == null || cfg.get().gitlabGroupId() == null) {
                    return null;
                }
                return new GitLabHealthCandidate(ws, cfg.get().gitlabGroupId(), cfg.get().gitlabWebhookId());
            })
            .filter(java.util.Objects::nonNull)
            .toList();

        if (gitLabWorkspaces.isEmpty()) return;

        int checked = 0,
            reregistered = 0;
        for (GitLabHealthCandidate candidate : gitLabWorkspaces) {
            Workspace workspace = candidate.workspace();
            try {
                Optional<WebhookInfo> existing = client.getGroupWebhook(
                    workspace.getId(),
                    candidate.groupId(),
                    candidate.webhookId()
                );
                checked++;

                if (existing.isEmpty()) {
                    log.warn(
                        "Webhook missing (likely auto-disabled), re-registering: workspaceId={}, webhookId={}",
                        workspace.getId(),
                        candidate.webhookId()
                    );
                    // Clear stored ID so registerWebhook creates a new one
                    updateGitLabConfig(workspace.getId(), cfg -> cfg.withGitlabWebhookId(null));

                    WebhookSetupResult result = registerWebhook(workspace);
                    if (result.registered()) {
                        reregistered++;
                        log.info(
                            "Re-registered webhook: workspaceId={}, newWebhookId={}",
                            workspace.getId(),
                            result.webhookId()
                        );
                    } else {
                        log.warn(
                            "Failed to re-register webhook: workspaceId={}, reason={}",
                            workspace.getId(),
                            result.failureReason()
                        );
                    }
                }
            } catch (Exception e) {
                log.debug("Webhook health check failed: workspaceId={}", workspace.getId(), e);
            }
        }

        if (reregistered > 0) {
            log.info("Webhook health check: checked={}, reregistered={}", checked, reregistered);
        }
    }

    private void clearWebhookFields(Workspace workspace) {
        updateGitLabConfig(workspace.getId(), cfg -> cfg.withGitlabWebhookId(null).withGitlabGroupId(null));
    }
}
