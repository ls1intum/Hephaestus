package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollectionRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentEventRepository;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Erases the workspace's Outline footprint when the workspace is purged: the mirrored document rows, the
 * collection registry, the per-document event log (actor subjects are personal data), and the upstream
 * change-notification subscription.
 *
 * <p>{@code WorkspaceStatus.PURGED} is a soft delete, so an {@code ON DELETE CASCADE} on
 * {@code workspace_id} would not fire — each module drops its own rows explicitly. Document bodies can carry
 * authored prose and names, so this erasure is the workspace-teardown arm of the GDPR story.
 *
 * <p>{@link #getOrder()} returns {@value #PURGE_ORDER} so this runs <b>before</b>
 * {@code ConnectionPurgeContributor} ({@code -100}, which transitions the Outline Connection to UNINSTALLED
 * and clears its token). Running while the Connection is still ACTIVE lets {@link OutlineWebhookRegistrar}
 * resolve the server URL and token to deregister the subscription upstream (best-effort, mirroring
 * {@code GitLabWebhookPurgeAdapter}) and clear the stored subscription id/secret — the same teardown the
 * connect {@code revoke} path performs — before dropping the mirrored bodies.
 *
 * <p>No {@code @Transactional} here: {@code WorkspaceLifecycleService#purgeWorkspace} runs the whole
 * contributor chain in one transaction, and {@code deleteByWorkspaceId} carries the {@code workspace_id}
 * predicate the tenancy inspector requires.
 */
@Component
@RequiredArgsConstructor
public class OutlineWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(OutlineWorkspacePurgeAdapter.class);

    /** Runs before {@code ConnectionPurgeContributor} ({@code -100}); must be strictly less than it. */
    static final int PURGE_ORDER = -200;

    private final OutlineDocumentRepository outlineDocumentRepository;
    private final OutlineCollectionRepository outlineCollectionRepository;
    private final OutlineDocumentEventRepository outlineDocumentEventRepository;

    /**
     * Absent when {@code hephaestus.integration.outline.enabled=false}: the registrar is conditional, but this
     * contributor is not, so it still drops any documents left over from a previously-enabled period.
     */
    private final ObjectProvider<OutlineWebhookRegistrar> webhookRegistrar;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Deregister the upstream subscription and clear its stored id/secret while the Connection is still
        // ACTIVE. Best-effort: a left-over subscription auto-disables upstream once deliveries start failing.
        try {
            webhookRegistrar.ifAvailable(registrar -> registrar.deregister(workspaceId));
        } catch (RuntimeException e) {
            log.warn(
                "outline.purge: subscription deregistration failed for workspaceId={}: {}",
                workspaceId,
                e.toString()
            );
        }
        outlineDocumentRepository.deleteByWorkspaceId(workspaceId);
        outlineCollectionRepository.deleteByWorkspaceId(workspaceId);
        // The event log carries actor subjects (personal data) — it erases with its workspace.
        outlineDocumentEventRepository.deleteByWorkspaceId(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
