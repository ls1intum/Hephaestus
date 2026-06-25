package de.tum.cit.aet.hephaestus.practices.finding.reaction;

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
 * <p>A student reacts to the feedback they were shown — not to an internal {@code Observation} — so the
 * reaction is anchored to the {@link Feedback} unit (ADR 0022). Append-only for research data integrity: a
 * developer submitting a second reaction creates a new row, preserving the temporal record of when they first
 * saw and then changed their mind about a unit. The latest row per (feedback, reactor) is the "current" state
 * for dashboard display.
 *
 * <p>Explicitly excluded from agent context (#895) — the AI must not know whether a developer previously
 * disputed feedback, to avoid contaminating accuracy measurement.
 *
 * @see Feedback for the delivered feedback unit being reacted to
 * @see FindingReactionAction for the action taxonomy
 */
@Entity
@Immutable
@Table(
    name = "reaction",
    indexes = {
        @Index(name = "idx_reaction_reactor_created", columnList = "reactor_user_id, created_at DESC"),
        @Index(name = "idx_reaction_feedback_reactor", columnList = "feedback_id, reactor_user_id, created_at DESC"),
        // A2 (ADR 0021): find a reaction by its stable locus across the detector's per-run re-detections.
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
     * The feedback unit this reaction is about. Uses DB-level {@code ON DELETE CASCADE}
     * so that deleting a feedback unit automatically cleans up its immutable reaction rows.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reaction_feedback"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /**
     * Direct access to the feedback ID without triggering a lazy load on the {@link #feedback} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     */
    @Column(name = "feedback_id", nullable = false, insertable = false, updatable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    /**
     * Denormalized copy of the reacted feedback's headline {@code Observation.recurrenceKey} (ADR 0021 C2),
     * captured at reaction-write time. The reacted feedback is per-run; its FK alone cannot locate this
     * reaction on a later run, but the {@code recurrence_key} is the stable (practice, target, subject, file)
     * locus that DOES recur, letting B2 suppression find a prior DISPUTED / NOT_APPLICABLE reaction against
     * a re-detected locus. Nullable: a reaction whose source locus predates C2 (null key) stays null and
     * simply cannot participate in B2.
     */
    @Column(name = "recurrence_key", length = 64)
    private String recurrenceKey;

    /**
     * The user who submitted this reaction — the feedback's recipient (only the recipient may react). Raw
     * {@code Long} FK to {@code user} (no {@code @ManyToOne}) to keep the cross-cutting identity columns
     * scalar, matching {@code Observation.aboutUserId}; the DB FK {@code fk_reaction_reactor} is
     * Liquibase-managed (RESTRICT — a reaction must survive independently of user lifecycle).
     */
    @NotNull
    @Column(name = "reactor_user_id", nullable = false)
    private Long reactorUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private FindingReactionAction action;

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
