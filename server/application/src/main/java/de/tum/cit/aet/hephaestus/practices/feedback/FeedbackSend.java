package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
        name = "feedback_send",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_feedback_send_workspace_id",
                    columnNames = {"feedback_id", "workspace_id"})
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSend {

    @Id
    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'POST'")
    @Column(name = "action", nullable = false, length = 8)
    private FeedbackSendAction action;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(name = "state", nullable = false, length = 16)
    private FeedbackSendState state;

    @Column(name = "lease_owner", nullable = true, length = 64)
    private @Nullable String leaseOwner;

    @Column(name = "lease_expires_at", nullable = true)
    private @Nullable Instant leaseExpiresAt;

    @ColumnDefault("0")
    @Column(name = "attempts", nullable = false)
    private int attempts;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", nullable = true, length = 512)
    private @Nullable String lastError;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
