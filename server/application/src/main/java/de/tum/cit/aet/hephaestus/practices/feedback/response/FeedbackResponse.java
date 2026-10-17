package de.tum.cit.aet.hephaestus.practices.feedback.response;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackUsefulness;
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
        name = "feedback_response",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_feedback_response_workspace_id",
                    columnNames = {"id", "workspace_id"})
        },
        indexes = {@Index(name = "idx_feedback_response_latest", columnList = "feedback_id, created_at DESC, id DESC")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = true, length = 16)
    private @Nullable FeedbackResolution resolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "usefulness", nullable = true, length = 16)
    private @Nullable FeedbackUsefulness usefulness;

    @Column(name = "comment", nullable = true, columnDefinition = "TEXT")
    private @Nullable String comment;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
