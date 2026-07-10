package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceSummaryQueryAdapter implements WorkspaceSummaryQuery {

    private final WorkspaceRepository workspaceRepository;

    WorkspaceSummaryQueryAdapter(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSummary> findById(long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(workspace ->
                new WorkspaceSummary(
                    workspace.getId(),
                    workspace.getWorkspaceSlug(),
                    workspace.getDisplayName(),
                    workspace.getMentorConfigId()
                )
            );
    }
}
