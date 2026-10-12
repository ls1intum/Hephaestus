package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.jspecify.annotations.Nullable;

/**
 * Immutable binding of a synthesized {@code Feedback} unit to the evidence it was composed from: one practice
 * {@link PracticeResult}, and optionally one of its {@code Observation}s when the feedback rests on that located
 * problem. A part that cites two problems of one result has two rows; a part that cites the result as a whole has one
 * row without an observation. Each negative result leads as a {@link EvidenceRole#PRIMARY} row, each positive one is
 * bound as a {@link EvidenceRole#SUPPORTING} row.
 *
 * <p>Append-only: written by the native, race-safe {@code insertIfAbsent} upsert ({@code FeedbackEvidenceRepository}),
 * which is idempotent on {@code uk_feedback_evidence_ref} ({@code NULLS NOT DISTINCT}, so one result-level row per
 * feedback and result). The schema proves tenancy and ancestry: the feedback, the result and the observation all
 * carry this row's workspace, and the observation belongs to this result (the {@code sfk_feedback_evidence_*}
 * composite keys). Every reference cascades, so the binding never outlives an endpoint.
 *
 * <p>This is the WADM <em>target</em> edge (which evidence a body of commentary is about); {@link FeedbackPlacement}
 * is the orthogonal WADM <em>selector</em> edge (where it is shown).
 *
 * @see Feedback for the synthesized unit being composed
 * @see PracticeResult for the evidence being bound
 * @see EvidenceRole for the PRIMARY/SUPPORTING weighting
 */
@Entity
@Immutable
@Table(
        name = "feedback_evidence",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_feedback_evidence_ref",
                    columnNames = {"feedback_id", "practice_result_id", "observation_id"})
        },
        indexes = {
            @Index(name = "idx_feedback_evidence_practice_result", columnList = "practice_result_id"),
            @Index(name = "idx_feedback_evidence_observation", columnList = "observation_id")
        })
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeedbackEvidence {

    @Id
    @EqualsAndHashCode.Include
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** The synthesized feedback unit; deleting it deletes its bindings. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "feedback_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_feedback_evidence_feedback"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /** The result bound into this feedback unit; deleting it deletes its bindings. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "practice_result_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_feedback_evidence_practice_result"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PracticeResult practiceResult;

    /**
     * The located problem of {@link #practiceResult} this feedback rests on; null when it rests on the result as a
     * whole, and on every binding written before problems were recorded. A plain id: {@code sfk_feedback_evidence_observation}
     * references {@code (id, practice_result_id, workspace_id)} of {@code practice_observation}.
     */
    @Column(name = "observation_id", columnDefinition = "UUID")
    private @Nullable UUID observationId;

    /**
     * Whether this result anchors the unit's headline ({@code PRIMARY}) or reinforces it
     * ({@code SUPPORTING}). PRIMARY is what carries the unit's severity and the F-4 policy floor
     * (every blocking result must surface under some PRIMARY row). Constrained to the two
     * values by {@code chk_feedback_evidence_role}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private EvidenceRole role;

    /** Stable render order of this binding within its feedback unit (lower = earlier). */
    @NotNull
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
