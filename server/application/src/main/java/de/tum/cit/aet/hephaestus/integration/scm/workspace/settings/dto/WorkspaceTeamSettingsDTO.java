package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto;

import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.WorkspaceTeamSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Team visibility settings for a specific workspace")
public record WorkspaceTeamSettingsDTO(
        @NonNull @Schema(description = "The workspace ID these settings belong to")
        Long workspaceId,

        @NonNull @Schema(description = "The team ID these settings apply to")
        Long teamId,

        @NonNull @Schema(description = "Whether the team is hidden from workspace activity")
        Boolean hidden) {
    public static WorkspaceTeamSettingsDTO from(WorkspaceTeamSettings settings) {
        return new WorkspaceTeamSettingsDTO(
                settings.getId().getWorkspaceId(), settings.getId().getTeamId(), settings.isHidden());
    }

    public static WorkspaceTeamSettingsDTO defaultSettings(Long workspaceId, Long teamId) {
        return new WorkspaceTeamSettingsDTO(workspaceId, teamId, false);
    }
}
