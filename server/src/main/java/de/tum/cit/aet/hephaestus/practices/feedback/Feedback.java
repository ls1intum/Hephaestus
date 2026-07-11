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
 * <p>Append-only for record integrity: a re-run that re-synthesises the same delivery unit inserts a new
 * row (deduplicated by the {@code (agent_job_id, position)} unit grain, {@code uk_feedback_unit}) and points
 * {@link #replacesId} at the prior row rather than mutating it, so the temporal record of what a student actually
 * saw is preserved. {@code baseline_state} is intentionally <em>not</em> a column — it is derived on read from the
 * supersession chain: a first-delivery row (no {@code replaces_id}) is "new", a row carrying a {@code replaces_id}
 * is "updated", and an unreplaced prior row is "superseded".
 *
 * <p>Cross-module references (agent job, workspace, recipient/about users) are stored as raw scalar ids — NOT
 * {@code @ManyToOne} — to avoid a Spring Modulith cycle between {@code practices} and the {@code agent} /
 * {@code workspace} / {@code scm} modules. The FK constraints (the {@code sfk_} prefix marks them Hibernate-invisible
 * scalar FKs) and their ON DELETE behaviour are managed by Liquibase: agent-job CASCADE, workspace/recipient/about
 * RESTRICT, self-replaces SET NULL — see each field.
 *
 * <p>Follows the {@link de.tum.cit.aet.hephaestus.practices.model.Observation} pattern: {@code @Immutable},
 * UUID PK assigned in {@code @PrePersist}, snake_case columns, string-stored enums.
 *
 * @see FeedbackDeliveryState for the delivery lifecycle
 * @see FeedbackChannel for the destination class
 * @see FeedbackSource for how the unit was produced
 * @see "ADR 0021 (findings↔feedback synthesis seam) and ADR 0022 §5, which fixes the final column names and the about_user_id firewall symmetry with Observation"
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
        @Index(name = "idx_feedback_replaces", columnList = "replaces_id"),
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
     * The agent job that produced this feedback unit. Scalar UUID (not {@code @ManyToOne}) to avoid a module cycle
     * between {@code practices} and {@code agent}. FK {@code sfk_feedback_agent_job} → {@code agent_job.id},
     * ON DELETE CASCADE — purging a job removes the feedback it synthesised.
     */
    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    /**
     * The workspace this feedback belongs to (tenancy scope). Scalar {@code Long} (not {@code @ManyToOne}) to avoid a
     * module cycle between {@code practices} and {@code workspace}. FK {@code sfk_feedback_workspace} →
     * {@code workspace.id} with no ON DELETE rule (RESTRICT): a workspace purge removes its feedback explicitly rather
     * than relying on a DB cascade.
     */
    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /**
     * The kind of work artifact this feedback is anchored to (PR / issue). NULL means the unit is not anchored to a
     * single artifact — e.g. a {@code PROFILE}-channel unit aggregated onto the recipient's dashboard. Constrained by
     * {@code chk_feedback_target_type} to {@code PULL_REQUEST} / {@code ISSUE} when present.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", length = 32)
    private WorkArtifact artifactType;

    /** External id of the target artifact (PR/issue). NULL in lockstep with {@link #artifactType} (unanchored unit). */
    @Column(name = "artifact_id")
    private Long artifactId;

    /**
     * Recipient side of the delivery firewall: the user this feedback is delivered <em>to</em>. Scalar {@code Long}
     * (not {@code @ManyToOne}) to avoid a cycle into the {@code scm} user module. FK {@code sfk_feedback_recipient} →
     * {@code user.id} with no ON DELETE rule (RESTRICT): the recipient cannot be deleted while feedback addressed to
     * them survives.
     */
    @NotNull
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    /**
     * Subject side of the delivery firewall: the user this feedback is <em>about</em> — never conflated with the
     * {@link #recipientUserId} it is delivered to. ALWAYS populated (symmetry with {@code Observation.aboutUserId}
     * and xAPI's mandatory, unambiguous Actor). For author-side feedback it equals {@code recipientUserId} (the
     * developer who receives the feedback is also its subject); for reviewer-side feedback the subject (e.g. the
     * reviewer) differs from the recipient. Modelled as its own column, not derived, so a reviewer-side unit needs
     * no schema change. Readers can trust it without a fallback, but must NOT assume it always differs from the
     * recipient. FK {@code sfk_feedback_subject} → {@code user.id} with no ON DELETE rule (RESTRICT): because the
     * column is NOT NULL, deleting the subject must be blocked rather than nulled.
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
     * 0-based position of this unit within its producing job's output. Pairs with {@code agent_job_id} as the
     * job-scoped dedup grain (unique {@code uk_feedback_unit}) and gives a stable delivery order — re-running the
     * same job re-derives the same {@code (agent_job_id, position)} unit rather than appending a duplicate.
     */
    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    /** Delivery lifecycle state (prepared → delivered / superseded / suppressed / failed). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false, length = 16)
    private FeedbackDeliveryState deliveryState;

    /** Why a unit was withheld. Set iff {@link #deliveryState} is {@code SUPPRESSED}; NULL otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suppression_reason", length = 32)
    private FeedbackSuppressionReason suppressionReason;

    /** The final rendered, student-facing body. NULL while still {@code PREPARED} or when suppressed. */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** Who authored this unit: the synthesis agent, a deterministic policy floor, or a fallback path. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FeedbackSource source;

    /**
     * Self-reference to the prior feedback row this unit replaces, when a re-run re-synthesised the same delivery
     * unit (the prior row is then read as {@code SUPERSEDED}). Scalar UUID self-FK for symmetry with the other ids.
     * FK {@code sfk_feedback_replaces} → {@code feedback.id}, ON DELETE SET NULL — purging an old row breaks the
     * back-link without deleting the survivor. Indexed by {@code idx_feedback_replaces} (covers the self-FK
     * ON DELETE). NULL for the first delivery of a unit.
     */
    @Column(name = "replaces_id", columnDefinition = "UUID")
    private UUID replacesId;

    /**
     * Cross-run continuity identity tying together successive deliveries of "the same" feedback as it evolves,
     * independent of which job produced it (so supersession survives the per-job {@code (agent_job_id, position)}
     * grain). Indexed by {@code idx_feedback_continuity} for chain lookups. NULL when continuity is not tracked.
     */
    @Column(name = "thread_key", length = 64)
    private String threadKey;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the unit was actually placed on its surface. NULL while {@code PREPARED}, suppressed, or failed. */
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
