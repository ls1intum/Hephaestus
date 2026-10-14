package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto;

import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.WorkspaceTeamRepositorySettings;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Repository contribution visibility settings for a specific team within a" + " workspace")
public record WorkspaceTeamRepositorySettingsDTO(
        @NonNull @Schema(description = "The workspace ID these settings belong to")
        Long workspaceId,

        @NonNull @Schema(description = "The team ID these settings apply to")
        Long teamId,

        @NonNull @Schema(description = "The repository ID these settings apply to")
        Long repositoryId,

        @NonNull @Schema(description = "Whether activity in this repository is left out of the team's" + " activity")
        Boolean hiddenFromContributions) {
    public static WorkspaceTeamRepositorySettingsDTO from(WorkspaceTeamRepositorySettings settings) {
        return new WorkspaceTeamRepositorySettingsDTO(
                settings.getId().getWorkspaceId(),
                settings.getId().getTeamId(),
                settings.getId().getRepositoryId(),
                settings.isHiddenFromContributions());
    }

    public static WorkspaceTeamRepositorySettingsDTO defaultSettings(Long workspaceId, Long teamId, Long repositoryId) {
        return new WorkspaceTeamRepositorySettingsDTO(workspaceId, teamId, repositoryId, false);
    }
}
