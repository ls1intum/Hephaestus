package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings;

import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceTeamSettingsPurgeContributor implements WorkspacePurgeContributor {
    private final WorkspaceTeamLabelFilterRepository labels;
    private final WorkspaceTeamRepositorySettingsRepository repositories;
    private final WorkspaceTeamSettingsRepository teams;

    @Override
    public int getOrder() {
        return -300;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        labels.deleteAllByWorkspaceId(workspaceId);
        repositories.deleteAllByWorkspaceId(workspaceId);
        teams.deleteAllByWorkspaceId(workspaceId);
    }
}
