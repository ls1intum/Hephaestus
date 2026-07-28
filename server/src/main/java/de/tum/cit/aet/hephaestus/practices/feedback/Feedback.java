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

/** One recipient-specific message composed from one or more observations. */
@Entity
@Immutable
@Table(
    name = "feedback",
    uniqueConstraints = { @UniqueConstraint(name = "uk_feedback_unit", columnNames = { "agent_job_id", "position" }) },
    indexes = {
        @Index(name = "idx_feedback_agent_job", columnList = "agent_job_id"),
        @Index(name = "idx_feedback_workspace", columnList = "workspace_id"),
        @Index(name = "idx_feedback_workspace_created", columnList = "workspace_id, created_at DESC, id DESC"),
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

    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", length = 32)
    private WorkArtifact artifactType;

    @Column(name = "artifact_id")
    private Long artifactId;

    @NotNull
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    /** The person whose work the message addresses; may be the recipient. */
    @NotNull
    @Column(name = "about_user_id", nullable = false)
    private Long aboutUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private FeedbackChannel channel;

    /** Stable, zero-based position within a job; part of {@code uk_feedback_unit}. */
    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false, length = 16)
    private FeedbackDeliveryState deliveryState;

    /** Why a unit was withheld. Set iff {@link #deliveryState} is {@code SUPPRESSED}; NULL otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suppression_reason", length = 32)
    private FeedbackSuppressionReason suppressionReason;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FeedbackSource source;

    @Column(name = "replaces_id", columnDefinition = "UUID")
    private UUID replacesId;

    /** Cross-run identity for successive versions of the same feedback. */
    @Column(name = "thread_key", length = 64)
    private String threadKey;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
