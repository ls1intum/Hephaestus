package de.tum.cit.aet.hephaestus.practices.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Immutable
@Table(
    name = "delivery_policy_evaluation",
    indexes = {
        @Index(name = "idx_delivery_policy_eval_job", columnList = "agent_job_id, evaluated_at DESC"),
        @Index(name = "idx_delivery_policy_eval_feedback", columnList = "feedback_id, evaluated_at DESC"),
        @Index(name = "idx_delivery_policy_eval_workspace", columnList = "workspace_id, evaluated_at DESC"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPolicyEvaluation {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    @Column(name = "feedback_id", columnDefinition = "UUID")
    private UUID feedbackId;

    @NotNull
    @Column(name = "admitted_revision", nullable = false)
    private Long admittedRevision;

    @Column(name = "evaluated_revision")
    private Long evaluatedRevision;

    @NotNull
    @Column(name = "resolver_version", nullable = false, length = 16)
    private String resolverVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false, length = 24)
    private DeliveryPolicySurface surface;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 24)
    private DeliveryPolicyStage stage;

    @NotNull
    @Column(name = "allowed", nullable = false)
    private Boolean allowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "decisive_reason", length = 48)
    private FeedbackSuppressionReason decisiveReason;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checks", nullable = false, columnDefinition = "jsonb")
    private JsonNode checks;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts", nullable = false, columnDefinition = "jsonb")
    private JsonNode facts;

    @NotNull
    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (evaluatedAt == null) evaluatedAt = Instant.now();
    }
}
