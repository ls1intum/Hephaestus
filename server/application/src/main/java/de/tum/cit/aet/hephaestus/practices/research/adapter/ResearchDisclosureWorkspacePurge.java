package de.tum.cit.aet.hephaestus.practices.research.adapter;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.research.ResearchDisclosureRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeBlockedException;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeGuard;
import org.springframework.stereotype.Component;

/**
 * A workspace whose released dataset may still sit with researchers cannot be purged: purging would
 * drop the only record of which keys to remove. Once every disclosure is closed, purge removes the
 * remaining receipts with the workspace.
 */
@Component
@ConditionalOnServerRole
public class ResearchDisclosureWorkspacePurge implements WorkspacePurgeGuard, WorkspacePurgeContributor {

    private final ResearchDisclosureRepository disclosures;

    public ResearchDisclosureWorkspacePurge(ResearchDisclosureRepository disclosures) {
        this.disclosures = disclosures;
    }

    @Override
    public void verifyQuiescent(Long workspaceId) {
        if (disclosures.hasOpen(workspaceId)) {
            throw new WorkspacePurgeBlockedException(
                    "This workspace has a released research dataset that is not closed. Record that the researchers"
                            + " deleted or anonymized it, then try again.");
        }
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        disclosures.deleteClosed(workspaceId);
    }
}
