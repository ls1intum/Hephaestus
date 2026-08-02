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
import jakarta.persistence.OneToOne;
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

@Entity
@Table(
    name = "practice",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = {
        @Index(name = "idx_practice_workspace_active", columnList = "workspace_id, is_active"),
        @Index(name = "idx_practice_practice_area", columnList = "practice_area_id"),
        @Index(name = "idx_practice_area_order", columnList = "practice_area_id, display_order"),
        @Index(name = "idx_practice_source_curated_slug", columnList = "source_curated_slug"),
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

    /**
     * Owning workspace (tenancy binding). {@code fk_practice_workspace} carries no DB cascade: a workspace
     * purge removes its practices explicitly in application code rather than relying on ON DELETE, so the
     * delete order over the dependent observation/revision graph stays under the purge contributor's control.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    /** Stable machine key, unique per workspace ({@code uk_practice_workspace_slug}); survives a {@link #name} rename. */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    /** Admin-renameable display label. */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 32)
    @ColumnDefault("'PULL_REQUEST'")
    private WorkArtifact artifactType = WorkArtifact.PULL_REQUEST;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_area_id", foreignKey = @ForeignKey(name = "fk_practice_area"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @ToString.Exclude
    private PracticeArea area;

    /** Catalog slug retained across workspace edits. */
    @Column(name = "source_curated_slug", length = 64)
    private String sourceCuratedSlug;

    /** Catalog comparison fingerprint captured when the workspace copy is created. */
    @Column(name = "source_curated_fingerprint", length = 64)
    private String sourceCuratedFingerprint;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id", foreignKey = @ForeignKey(name = "fk_practice_current_revision"))
    @ToString.Exclude
    private PracticeRevision currentRevision;

    @Column(name = "display_order", nullable = false)
    @ColumnDefault("0")
    private int displayOrder = 0;

    /**
     * The domain events that gate detection for this practice (e.g. {@code PullRequestCreated},
     * {@code ReviewSubmitted}), stored as a JSONB array. The trigger gate fires detection only when the
     * incoming event is in this set, so the rule's lifecycle is bound to the artifact events it cares about.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb", nullable = false)
    @ToString.Exclude
    private JsonNode triggerEvents;

    /**
     * The detection rubric the agent evaluates the artifact against — the rule's normative text, never shown
     * to learners. The {@code DEFECT-DETECTOR DISCIPLINE} marker token (see {@link #isDefectDetector()}) lives
     * in this text.
     */
    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    @ToString.Exclude
    private String criteria;

    /**
     * Developer-facing rationale: one or two sentences on WHY this practice matters — the cost it averts or
     * the value it adds — in plain language a learner reads, never the detection rubric. Part of the
     * learner-facing layer. MUST NOT leak detection vocabulary (PRESENT/ABSENT/GOOD/BAD/NOT_APPLICABLE);
     * the same authoring guard that covers {@link #whatGoodLooksLike} rejects detector vocabulary here too.
     * Nullable; surfaced only in {@code LearnerPracticeDTO}, never alongside {@link #criteria}.
     */
    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    @ToString.Exclude
    private String whyItMatters;

    /**
     * Developer-facing exemplar: a short, concrete picture of what doing this well looks like (an instance,
     * not the rubric). MUST NOT restate the {@link #criteria} or leak detection vocabulary
     * (PRESENT/ABSENT/GOOD/BAD/NOT_APPLICABLE); enforced by an authoring guard. Nullable; learner-facing only.
     */
    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    @ToString.Exclude
    private String whatGoodLooksLike;

    /**
     * Optional Bun/TypeScript static analysis script that runs before the AI agent.
     * Produces structured hints (not observations) that the agent uses as starting points.
     * When null, no precomputation runs for this practice.
     */
    @Column(name = "precompute_script", columnDefinition = "TEXT")
    @ToString.Exclude
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
     * has no legal {@code (PRESENT, GOOD)} clean-bill-of-health observation (a clean surface is NOT_APPLICABLE, never
     * a strength to endorse). The detection and delivery layers coerce/suppress accordingly; keeping the rule
     * here keeps it in one place.
     *
     * <p>{@code DEFECT-DETECTOR DISCIPLINE} is a LOAD-BEARING marker token, matched verbatim. An admin who edits
     * a defect-detector's {@link #criteria} must preserve it exactly — dropping or reformatting it (lowercasing,
     * hyphen→space, wrapping the token across a line) silently flips the practice into an ordinary one and
     * re-enables the false {@code (PRESENT, GOOD)} strength this firewall exists to block.
     */
    public boolean isDefectDetector() {
        return criteria != null && criteria.contains("DEFECT-DETECTOR DISCIPLINE");
    }
}
