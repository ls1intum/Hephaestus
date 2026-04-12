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
 * <p>Top-level feature flags default to {@code false} for new workspaces —
 * admins enable features manually via the admin UI. Sub-feature toggles
 * (e.g., trigger modes) default to {@code true} so enabling the parent
 * feature activates all sub-features immediately.
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

    /** Whether the agent-based practice review feature is enabled */
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

    /** Whether the league tiers and rankings are enabled */
    @Column(name = "leagues_enabled", nullable = false)
    @NotNull(message = "Leagues enabled flag is required")
    private Boolean leaguesEnabled = false;

    /** Whether practice reviews are automatically triggered by PR events */
    @Column(name = "practice_review_auto_trigger_enabled", nullable = false)
    @NotNull(message = "Practice review auto-trigger flag is required")
    private Boolean practiceReviewAutoTriggerEnabled = true;

    /** Whether practice reviews can be manually triggered via bot command */
    @Column(name = "practice_review_manual_trigger_enabled", nullable = false)
    @NotNull(message = "Practice review manual trigger flag is required")
    private Boolean practiceReviewManualTriggerEnabled = true;

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
        if (request.leaguesEnabled() != null) {
            this.leaguesEnabled = request.leaguesEnabled();
        }
        if (request.practiceReviewAutoTriggerEnabled() != null) {
            this.practiceReviewAutoTriggerEnabled = request.practiceReviewAutoTriggerEnabled();
        }
        if (request.practiceReviewManualTriggerEnabled() != null) {
            this.practiceReviewManualTriggerEnabled = request.practiceReviewManualTriggerEnabled();
        }
    }
}
