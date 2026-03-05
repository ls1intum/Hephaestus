package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.workspace.GitLabWebhookService;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deregisters the GitLab group webhook when a workspace is purged.
 *
 * <p>Runs at order 50 — early in the purge sequence, before repository monitors
 * or git clones are cleaned up. This ensures the webhook is removed while
 * the workspace entity and its credentials are still intact for API calls.
 *
 * <p>Deregistration is best-effort: failures are logged but never block the purge.
 */
@Component
public class GitLabWebhookPurgeContributor implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookPurgeContributor.class);

    private final GitLabWebhookService webhookService;

    public GitLabWebhookPurgeContributor(GitLabWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        try {
            webhookService.deregisterWebhookByWorkspaceId(workspaceId);
        } catch (Exception e) {
            log.warn(
                "GitLab webhook deregistration failed during purge (best-effort): workspaceId={}, error={}",
                workspaceId,
                e.getMessage()
            );
        }
    }

    @Override
    public int getOrder() {
        // Early in purge sequence — before repo monitors (step 5) and git clones (200).
        // Credentials must still be accessible for the API call.
        return 50;
    }
}
