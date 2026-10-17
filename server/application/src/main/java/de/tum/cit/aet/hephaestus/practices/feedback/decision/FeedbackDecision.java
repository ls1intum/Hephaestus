package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

@Entity
@Immutable
@Table(
        name = "feedback_decision",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_feedback_decision_workspace_id",
                    columnNames = {"id", "workspace_id"})
        },
        indexes = {@Index(name = "idx_feedback_decision_latest", columnList = "feedback_id, decided_at DESC, id DESC")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDecision {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 8)
    private FeedbackDecisionAction action;

    @Column(name = "actor_account_id", nullable = true)
    private @Nullable Long actorAccountId;

    @Column(name = "reason", nullable = true, length = 500)
    private @Nullable String reason;

    @Column(name = "note", nullable = true, columnDefinition = "TEXT")
    private @Nullable String note;

    @Column(name = "content_digest", nullable = true, length = 64)
    private @Nullable String contentDigest;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
