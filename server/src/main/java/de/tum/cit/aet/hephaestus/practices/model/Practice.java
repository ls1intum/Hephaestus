package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * Workspace-scoped practice definition for evaluating developer contributions.
 *
 * <p>A practice represents a specific coding or process standard that can be
 * detected in pull requests, commits, or reviews. Each practice belongs to a
 * workspace and is identified by a unique slug within that workspace.
 *
 * <p>The {@link #triggerEvents} field (JSONB) specifies which domain events
 * should trigger detection for this practice (e.g., PullRequestCreated, ReviewSubmitted).
 *
 * @see PracticeFinding for detection results
 */
@Entity
@Table(
    name = "practice",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = {
        @Index(name = "idx_practice_workspace_active", columnList = "workspace_id, is_active"),
        // Area-scoped reads (Reflection/Facilitator dashboards) join finding→practice→area; index the FK.
        @Index(name = "idx_practice_practice_area", columnList = "practice_area_id"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Practice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * The artifact this practice targets (PR vs ISSUE). The discriminator that routes the trigger
     * gate, the case-context builder, the {@code AgentJobType}/handler, and the delivery surface.
     * NOT NULL; defaults to {@code PULL_REQUEST} for backward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 32)
    @ColumnDefault("'PULL_REQUEST'")
    private WorkArtifact artifactType = WorkArtifact.PULL_REQUEST;

    /**
     * Whether this practice describes a desirable habit, an anti-pattern, or a context-dependent
     * pattern. Supplies the good/bad direction that {@link Observation} omits, so {@code OBSERVED} can
     * mean "strength" for one practice and "problem" for another (see ADR 0021, F-6). NOT NULL;
     * defaults to {@code DESIRABLE} — every catalogued practice today is a desirable habit.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "polarity", nullable = false, length = 16)
    @ColumnDefault("'DESIRABLE'")
    private Polarity polarity = Polarity.DESIRABLE;

    /**
     * Whose conduct this practice evaluates — the contribution author or its reviewer (ADR 0021, C2).
     * Drives the {@code subject_user_id} a finding is filed against and the audience a {@code Feedback}
     * unit is delivered to, so reviewer-craft lessons reach the reviewer and never the author. NOT NULL;
     * defaults to {@code AUTHOR} — every catalogued practice today is author-side (zero backfill).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_role", nullable = false, length = 16)
    @ColumnDefault("'AUTHOR'")
    private SubjectRole subjectRole = SubjectRole.AUTHOR;

    /**
     * Optional {@link PracticeArea} this practice rolls up to (NULL = ungrouped). 1:N (one area owns
     * many practices; a practice belongs to at most one area): the single owning bucket keeps the
     * per-area acted-on/total progress denominator unambiguous. Do not loosen to a join table without
     * also switching progress math to per-(area, practice) rows.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_area_id", foreignKey = @ForeignKey(name = "fk_practice_area"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @ToString.Exclude
    private PracticeArea area;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb", nullable = false)
    private JsonNode triggerEvents;

    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    private String criteria;

    /**
     * Developer-facing rationale: one or two sentences on WHY this practice matters — the cost it averts or
     * the value it adds — in plain language a learner reads, never the detection rubric. Part of the
     * learner-facing layer. Nullable; surfaced only in {@code LearnerPracticeDTO}, never alongside
     * {@link #criteria}.
     */
    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private String whyItMatters;

    /**
     * Developer-facing exemplar: a short, concrete picture of what doing this well looks like (an instance,
     * not the rubric). MUST NOT restate the {@link #criteria} or leak detection vocabulary
     * (OBSERVED/NOT_OBSERVED); enforced by an authoring guard. Nullable; learner-facing only.
     */
    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    private String whatGoodLooksLike;

    /**
     * Optional Bun/TypeScript static analysis script that runs before the AI agent.
     * Produces structured hints (not verdicts) that the agent uses as starting points.
     * When null, no precomputation runs for this practice.
     */
    @Column(name = "precompute_script", columnDefinition = "TEXT")
    private String precomputeScript;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Whether this practice is a defect-detector — its criteria declare {@code DEFECT-DETECTOR DISCIPLINE}, so it
     * has no legal OBSERVED verdict (a clean surface is NOT_APPLICABLE, never a strength to endorse). The detection
     * and delivery layers coerce/suppress accordingly; keeping the rule here keeps it in one place.
     */
    public boolean isDefectDetector() {
        return criteria != null && criteria.contains("DEFECT-DETECTOR DISCIPLINE");
    }
}
