package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes retained occasion evidence with the content it copied, leaving only the statement that it was erased.
 * Occasion rows stay: reviews reference them, and the workspace row outlives its purge.
 */
@Component
@RequiredArgsConstructor
public class ItemEventEvidenceErasure implements WorkspacePurgeContributor {
    private final ItemEventRepository repository;

    @Override
    @Transactional
    public void deleteWorkspaceData(Long workspaceId) {
        repository.forgetWorkspaceEvidence(workspaceId, "%", ItemEventInput.ERASED);
    }

    @EventListener
    @Transactional
    public void onScmMirrorErased(ScmMirrorErasedEvent event) {
        repository.forgetWorkspaceEvidence(event.workspaceId(), "scm.%", ItemEventInput.ERASED);
    }
}
