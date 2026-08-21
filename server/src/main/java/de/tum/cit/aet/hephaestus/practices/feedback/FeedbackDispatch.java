package de.tum.cit.aet.hephaestus.practices.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(
    name = "feedback_dispatch",
    uniqueConstraints = @UniqueConstraint(name = "uk_feedback_dispatch_key", columnNames = "destination_key"),
    indexes = {
        @Index(name = "idx_feedback_dispatch_recovery", columnList = "state, lease_expires_at, updated_at"),
        @Index(name = "idx_feedback_dispatch_workspace", columnList = "workspace_id, created_at DESC"),
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDispatch {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "destination_key", nullable = false, length = 96)
    private String destinationKey;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    @Column(name = "feedback_id", columnDefinition = "UUID")
    private UUID feedbackId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "destination", nullable = false, length = 40)
    private FeedbackDispatchDestination destination;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private FeedbackDispatchState state;

    @NotNull
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "target_external_ref", length = 255)
    private String targetExternalRef;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_slugs", nullable = false, columnDefinition = "jsonb")
    private JsonNode practiceSlugs;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "write_started", nullable = false)
    private Boolean writeStarted;

    @Column(name = "delivered_external_ref", length = 255)
    private String deliveredExternalRef;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @NotNull
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
