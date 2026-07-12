package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.workspace.dto.UpdateWorkspaceFeaturesRequestDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Workspace-scoped feature flags, exposed by the admin UI. Top-level flags default to
 * {@code false} (new workspaces opt in); sub-feature trigger flags default to {@code true}
 * (enabling the parent activates all triggers). Every {@code @ColumnDefault} mirrors the
 * Liquibase default so Hibernate's hbm2ddl validation does not drift from the migration.
 *
 * @see UpdateWorkspaceFeaturesRequestDTO
 */
@Embeddable
@Getter
@Setter
public class WorkspaceFeatures {

    @NotNull
    @ColumnDefault("false")
    @Column(name = "practices_enabled", nullable = false)
    private Boolean practicesEnabled = false;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "mentor_enabled", nullable = false)
    private Boolean mentorEnabled = false;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "achievements_enabled", nullable = false)
    private Boolean achievementsEnabled = false;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "practice_review_auto_trigger_enabled", nullable = false)
    private Boolean practiceReviewAutoTriggerEnabled = true;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "practice_review_manual_trigger_enabled", nullable = false)
    private Boolean practiceReviewManualTriggerEnabled = true;

    /**
     * Audience for the k-anonymised workspace health aggregate on the practice overview (a privacy control,
     * Defaults to {@link HealthVisibility#MENTORS_ONLY}. The roster and
     * per-developer drill-down stay admin/owner-only in both values; see {@link HealthVisibility}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MENTORS_ONLY'")
    @Column(name = "health_visibility", nullable = false, length = 32)
    private HealthVisibility healthVisibility = HealthVisibility.MENTORS_ONLY;

    /** PATCH semantics: null fields are ignored, non-null fields overwrite. */
    public void applyPatch(UpdateWorkspaceFeaturesRequestDTO request) {
        if (request.practicesEnabled() != null) this.practicesEnabled = request.practicesEnabled();
        if (request.mentorEnabled() != null) this.mentorEnabled = request.mentorEnabled();
        if (request.achievementsEnabled() != null) this.achievementsEnabled = request.achievementsEnabled();
        if (request.practiceReviewAutoTriggerEnabled() != null) {
            this.practiceReviewAutoTriggerEnabled = request.practiceReviewAutoTriggerEnabled();
        }
        if (request.practiceReviewManualTriggerEnabled() != null) {
            this.practiceReviewManualTriggerEnabled = request.practiceReviewManualTriggerEnabled();
        }
        if (request.healthVisibility() != null) {
            this.healthVisibility = request.healthVisibility();
        }
    }
}
