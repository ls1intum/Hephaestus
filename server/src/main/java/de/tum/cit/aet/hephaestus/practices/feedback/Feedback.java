package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Immutable record of one synthesised unit of feedback delivered (or withheld) for a single recipient.
 *
 * <p>Where a {@link de.tum.cit.aet.hephaestus.practices.model.Observation} is the raw, audience-neutral
 * observation an agent made, a {@code Feedback} is the author-facing (or reviewer-facing) <em>delivery</em> of
 * one or more of those findings: it carries the rendered body, the surface it was placed on, the synthesis
 * provenance, and its delivery {@link FeedbackDeliveryState}. Findings are linked through the {@code FeedbackObservation}
 * join and physically placed through {@code FeedbackPlacement} rows — see ADR 0021 (findings↔feedback synthesis seam).
 *
 * <p>Append-only for research-data integrity: a re-run that re-synthesises the same delivery unit inserts a new
 * row (deduplicated by the {@code (agent_job_id, position)} unit grain) and points {@link #replacesId} at the
 * prior row rather than mutating it, so the temporal record of what a student actually saw is preserved.
 * {@code baseline_state} is
 * intentionally <em>not</em> a column — it is derived on read from the supersession chain.
 *
 * <p>Cross-module references (agent job, workspace, recipient/subject users) are stored as raw scalar ids — NOT
 * {@code @ManyToOne} — to avoid a Spring Modulith cycle between {@code practices} and the {@code agent} /
 * {@code workspace} / {@code scm} modules. The corresponding FK constraints are managed by Liquibase at the DB level.
 *
 * <p>Follows the {@link de.tum.cit.aet.hephaestus.practices.model.Observation} pattern: {@code @Immutable},
 * UUID PK assigned in {@code @PrePersist}, snake_case columns, string-stored enums.
 *
 * @see FeedbackDeliveryState for the delivery lifecycle
 * @see FeedbackChannel for the destination class
 * @see FeedbackSource for how the unit was produced
 */
@Entity
@Immutable
@Table(
    name = "feedback",
    uniqueConstraints = { @UniqueConstraint(name = "uk_feedback_unit", columnNames = { "agent_job_id", "position" }) },
    indexes = {
        @Index(name = "idx_feedback_agent_job", columnList = "agent_job_id"),
        @Index(name = "idx_feedback_workspace", columnList = "workspace_id"),
        @Index(name = "idx_feedback_recipient_created", columnList = "recipient_user_id, created_at DESC"),
        @Index(name = "idx_feedback_target", columnList = "artifact_type, artifact_id"),
        @Index(name = "idx_feedback_continuity", columnList = "thread_key"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The agent job that produced this feedback unit. Raw UUID (not {@code @ManyToOne}) to avoid a module cycle
     * between {@code practices} and {@code agent}; the FK constraint {@code fk_feedback_agent_job} is managed by
     * Liquibase at the DB level.
     */
    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    /**
     * The workspace this feedback belongs to. Raw {@code Long} (not {@code @ManyToOne}) to avoid a module cycle
     * between {@code practices} and {@code workspace}; FK {@code fk_feedback_workspace} managed by Liquibase.
     */
    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /**
     * The kind of work artifact this feedback is about (PR / issue). Nullable: a reflection-dashboard unit
     * is not anchored to a single artifact.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", length = 32)
    private WorkArtifact artifactType;

    /** External id of the target artifact (PR/issue). Nullable in lockstep with {@link #artifactType}. */
    @Column(name = "artifact_id")
    private Long artifactId;

    /**
     * The user this feedback is delivered to. Raw {@code Long} (not {@code @ManyToOne}) to avoid a cycle into the
     * {@code scm} user module; FK {@code fk_feedback_recipient} managed by Liquibase.
     */
    @NotNull
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    /**
     * The user this feedback is <em>about</em> — ALWAYS populated (symmetry with
     * {@code Observation.aboutUserId} and xAPI's mandatory, unambiguous Actor). Today every writer sets this
     * equal to {@link #recipientUserId} (the author-side catalogue: the developer who receives the feedback is
     * also its subject); the column is modelled separately, not derived, to leave room for a future reviewer-side
     * unit whose subject differs from its recipient without a schema change. Readers can trust it without a
     * fallback; they must NOT assume it always differs from the recipient.
     */
    @NotNull
    @Column(name = "about_user_id", nullable = false)
    private Long aboutUserId;

    /** Destination class for this unit (in-context comment, conversation turn, profile). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private FeedbackChannel channel;

    /**
     * 0-based position of this unit within its producing job's output. Pairs with {@code agent_job_id} to form
     * the idempotency key and gives a stable delivery order.
     */
    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    /** Delivery lifecycle state (prepared → delivered / superseded / suppressed / failed). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false, length = 16)
    private FeedbackDeliveryState deliveryState;

    /** Why a unit was withheld, when {@link #deliveryState} is {@code SUPPRESSED}. Null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suppression_reason", length = 32)
    private FeedbackSuppressionReason suppressionReason;

    /** The final rendered, student-facing body. Null while still {@code PREPARED} or when suppressed. */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** Who authored this unit: the synthesis agent, a deterministic policy floor, or a fallback path. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FeedbackSource source;

    /**
     * Self-reference to the prior feedback row this unit replaces, when a re-run re-synthesised the same delivery
     * unit. Raw UUID self-FK (kept scalar for symmetry with the other ids); FK {@code fk_feedback_supersedes}
     * managed by Liquibase. Null for the first delivery of a unit.
     */
    @Column(name = "replaces_id", columnDefinition = "UUID")
    private UUID replacesId;

    /**
     * Cross-run continuity identity tying together successive deliveries of “the same” feedback as it evolves,
     * independent of which job produced it. Indexed for chain lookups. Null when continuity is not tracked.
     */
    @Column(name = "thread_key", length = 64)
    private String threadKey;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the unit was actually delivered to its surface. Null while {@code PREPARED}, suppressed, or failed. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

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
