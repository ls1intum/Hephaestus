package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceFeaturesRequestDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Embeddable value object grouping workspace-scoped feature flags.
 *
 * <p>All flags default to {@code false} for new workspaces — admins enable
 * features manually via the admin UI. This class centralizes feature flag
 * access and mutation, keeping the {@link Workspace} entity lean.
 *
 * <p>Adding a new feature flag requires only:
 * <ol>
 *   <li>Add a new field here</li>
 *   <li>Add a Liquibase {@code addColumn} migration</li>
 *   <li>Add the field to {@link UpdateWorkspaceFeaturesRequestDTO}</li>
 *   <li>Add the field to the DTO mappers and frontend types</li>
 * </ol>
 *
 * @see UpdateWorkspaceFeaturesRequestDTO
 */
@Embeddable
@Getter
@Setter
public class WorkspaceFeatures {

    /** Whether the best practices detection and tracking feature is enabled */
    @Column(name = "practices_enabled", nullable = false)
    @NotNull(message = "Practices enabled flag is required")
    private Boolean practicesEnabled = false;

    /** Whether the achievements system (badges, skill trees) is enabled */
    @Column(name = "achievements_enabled", nullable = false)
    @NotNull(message = "Achievements enabled flag is required")
    private Boolean achievementsEnabled = false;

    /** Whether the leaderboard ranking page is enabled */
    @Column(name = "leaderboard_enabled", nullable = false)
    @NotNull(message = "Leaderboard enabled flag is required")
    private Boolean leaderboardEnabled = false;

    /** Whether the league/progression system is enabled */
    @Column(name = "progression_enabled", nullable = false)
    @NotNull(message = "Progression enabled flag is required")
    private Boolean progressionEnabled = false;

    /**
     * Applies a partial update from the request DTO (PATCH semantics).
     * Null fields in the request are ignored; non-null fields overwrite the current value.
     */
    public void applyPatch(UpdateWorkspaceFeaturesRequestDTO request) {
        if (request.practicesEnabled() != null) {
            this.practicesEnabled = request.practicesEnabled();
        }
        if (request.achievementsEnabled() != null) {
            this.achievementsEnabled = request.achievementsEnabled();
        }
        if (request.leaderboardEnabled() != null) {
            this.leaderboardEnabled = request.leaderboardEnabled();
        }
        if (request.progressionEnabled() != null) {
            this.progressionEnabled = request.progressionEnabled();
        }
    }
}
