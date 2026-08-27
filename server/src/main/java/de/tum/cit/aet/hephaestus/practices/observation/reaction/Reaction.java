package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
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
import org.jspecify.annotations.Nullable;

/**
 * Immutable record of a developer's combined response to a delivered unit of {@link Feedback}.
 *
 * <p>A developer reacts to the feedback they were shown — not to an internal {@code Observation} — so the row
 * anchors on the {@link Feedback} unit (ADR 0022). {@code @Immutable} + append-only: a second reaction to the
 * same unit inserts a new row rather than mutating the first, so the temporal record of an initial response
 * and a later change of mind is preserved for research.
 *
 * <p><b>A row is a delta, not a snapshot.</b> It carries exactly what the recipient said at that moment, and
 * each of the two dimensions is null where they said nothing. That is deliberate: the research record must be
 * able to show that someone rated a unit helpful on Monday and disputed it on Thursday without inventing a
 * Monday dispute. The consequence is that the CURRENT state is not the newest row — it is the newest non-null
 * value of each dimension independently, which {@code ReactionRepository} computes and is the only correct way
 * to read this table.
 *
 * <p><b>A row with neither dimension is a withdrawal.</b> It is how a recipient takes an answer back, and it
 * ends the run of rows before it: nothing older than the newest withdrawal speaks for them any more. Without
 * it an append-only table could only ever accumulate, and a mis-click would be permanent.
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
 * @see Feedback for the delivered piece of feedback being reacted to
 * @see FeedbackResolution for the resolution taxonomy
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
     *     reliable when it was set in sync with {@code feedback} (as {@code FeedbackResponseService.submitResponse}
     *     does). Never rely on a builder-set {@code feedbackId} alone post-persist.
     */
    @Column(name = "feedback_id", nullable = false, insertable = false, updatable = false, columnDefinition = "UUID")
    private UUID feedbackId;

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

    /** How useful the recipient found the unit; null when this row did not answer that question. */
    @Enumerated(EnumType.STRING)
    @Column(name = "usefulness", length = 16)
    private @Nullable FeedbackUsefulness usefulness;

    /**
     * What the recipient decided to do; null when this row did not answer that question.
     *
     * <p>Column {@code action} rather than {@code resolution}: the column shipped under that name and a
     * released one is renamed only across two releases. The field says what the value means.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 16)
    private @Nullable FeedbackResolution resolution;

    /**
     * The recipient's free-text rationale. NULL means none was given. Coupled to {@link #action} by the DB
     * CHECK {@code chk_reaction_disputed_explanation}: a {@link FeedbackResolution#DISPUTED} row must carry a
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
