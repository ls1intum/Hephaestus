package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Immutable record of a developer's reaction to a delivered unit of {@link Feedback}.
 *
 * <p>A developer reacts to the feedback they were shown — not to an internal {@code Observation} — so the row
 * anchors on the {@link Feedback} unit (ADR 0022). {@code @Immutable} + append-only: a second reaction to the
 * same unit inserts a new row rather than mutating the first, so the temporal record of an initial response
 * and a later change of mind is preserved for research. The latest row per (feedback, reactor) is the
 * "current" state for dashboard display.
 *
 * <p>Anchoring on the delivered unit is the reviewer-side firewall: a reaction is always about, and submitted
 * by, the unit's recipient, so this table never holds a judgement about a third party. The about-vs-recipient
 * distinction lives on {@link Feedback}, not here.
 *
 * <p>Excluded from agent context — the detector must not learn whether a developer disputed earlier feedback,
 * which would contaminate accuracy measurement. The only sanctioned reader is {@code ReactionSuppressionFilter}
 * (cross-run re-nag suppression), which reads DISPUTED / NOT_APPLICABLE reactions but never feeds reaction
 * content into the detector prompt.
 *
 * @see Feedback for the delivered feedback unit being reacted to
 * @see ReactionAction for the action taxonomy
 */
@Entity
@Immutable
@Table(
    name = "reaction",
    indexes = {
        // Per-developer engagement timeline (most-recent first).
        @Index(name = "idx_reaction_reactor_created", columnList = "reactor_user_id, created_at DESC"),
        // Resolve the latest reaction for a given (feedback, reactor) — the "current state" lookup.
        @Index(name = "idx_reaction_feedback_reactor", columnList = "feedback_id, reactor_user_id, created_at DESC"),
        // Cross-run re-nag suppression: locate a prior reaction by its stable locus across per-run re-detections,
        // since the per-run feedback FK does not recur but the recurrence_key does.
        @Index(name = "idx_reaction_correlation", columnList = "recurrence_key"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The delivered {@link Feedback} unit this reaction responds to. DB FK {@code fk_reaction_feedback} with
     * {@code ON DELETE CASCADE}: a reaction has no meaning without the unit it reacts to, so deleting the unit
     * removes its immutable reaction rows rather than orphaning them.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reaction_feedback"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /**
     * Direct access to the feedback ID without triggering a lazy load on the {@link #feedback} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     *
     * @implNote Because this column is {@code insertable=false/updatable=false}, a builder-set
     *     {@code .feedbackId(...)} is NOT persisted and is NOT repopulated from the association after
     *     {@code save()}. Callers MUST set {@link #feedback}; the in-memory {@code feedbackId} is only
     *     reliable when it was set in sync with {@code feedback} (as {@code ReactionService.submitReaction}
     *     does). Never rely on a builder-set {@code feedbackId} alone post-persist.
     */
    @Column(name = "feedback_id", nullable = false, insertable = false, updatable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    /**
     * Denormalized copy of the reacted feedback's headline {@code Observation.recurrenceKey}, captured at
     * reaction-write time. Stored, not joined: the reacted feedback unit is per-run, so its FK alone cannot
     * locate this reaction on a later run, but the {@code recurrence_key} is the stable (practice, target,
     * subject, file) locus that DOES recur. Re-nag suppression matches on it to find a prior DISPUTED /
     * NOT_APPLICABLE reaction against a re-detected locus — the cross-run grain, distinct from the per-occurrence
     * {@code Observation.occurrenceKey}. NULL means the source unit bound no PRIMARY observation carrying a
     * non-null {@code recurrence_key} ({@code FeedbackRepository.findHeadlineRecurrenceKey} skips null-key PRIMARY
     * rows and takes the earliest one that has a key), so the reaction cannot participate in cross-run suppression.
     *
     * <p><b>Headline-only by design.</b> A {@link Feedback} unit can fuse several findings (one PRIMARY per
     * problem, ADR 0022), but only the headline locus is captured here. So when a recipient disputes a
     * multi-finding unit, suppression matches only the headline locus on re-run; the non-headline loci bundled
     * into the same unit recur and may re-nag. This is the known suppression grain, not a defect. Full-unit
     * suppression would require capturing every member recurrence key (e.g. a child {@code reaction_locus}
     * table keyed off {@code FeedbackObservation}), which is out of scope.
     */
    @Column(name = "recurrence_key", length = 64)
    private String recurrenceKey;

    /**
     * The user who submitted this reaction — always the feedback's recipient, since only the recipient may
     * react. Scalar {@code Long} FK to {@code user} with no {@code @ManyToOne}, matching the identity columns
     * elsewhere (e.g. {@code Observation.aboutUserId}): the relationship is declared in Liquibase as the
     * Hibernate-invisible {@code sfk_reaction_reactor}, keeping the cross-module user reference out of the JPA
     * graph and off the schema-drift gate. {@code ON DELETE RESTRICT} (the default, no cascade) — a reaction is
     * research evidence and must not vanish with a user-row deletion.
     */
    @NotNull
    @Column(name = "reactor_user_id", nullable = false)
    private Long reactorUserId;

    /**
     * What the recipient did with the unit. Persisted as the enum name; the DB CHECK
     * {@code chk_reaction_action} pins the column to the {@link ReactionAction} value set.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private ReactionAction action;

    /**
     * The recipient's free-text rationale. NULL means none was given. Coupled to {@link #action} by the DB
     * CHECK {@code chk_reaction_disputed_explanation}: a {@link ReactionAction#DISPUTED} row must carry a
     * non-blank explanation (the reasoned rejection IS the evaluative judgement), while {@code ADDRESSED} and
     * {@code NOT_APPLICABLE} may leave it NULL.
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
