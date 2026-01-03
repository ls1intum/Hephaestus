package de.tum.in.www1.hephaestus.activity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * Dead letter storage for activity events that failed to record after retry exhaustion.
 *
 * <p>This entity provides durable persistence for failed events, enabling:
 * <ul>
 *   <li><strong>Manual investigation:</strong> Operators can query failed events by error type, time range, etc.</li>
 *   <li><strong>Automated retry:</strong> A scheduled job can attempt to reprocess unresolved events</li>
 *   <li><strong>Metrics/alerting:</strong> Dashboard visibility into failure patterns</li>
 *   <li><strong>Audit trail:</strong> Complete record of what was lost and why</li>
 * </ul>
 *
 * <h3>Resolution Workflow</h3>
 * <ol>
 *   <li>Event fails after retry exhaustion → stored here with status=PENDING</li>
 *   <li>Scheduled job or operator investigates the failure</li>
 *   <li>If transient: retry → on success, mark RESOLVED</li>
 *   <li>If permanent (bad data): mark DISCARDED with resolution notes</li>
 * </ol>
 *
 * <h3>Retention Policy</h3>
 * <p>Resolved/discarded events should be purged after 30 days via scheduled cleanup.
 * Pending events are kept indefinitely until resolved.
 *
 * @see ActivityEventService#recoverFromTransientError
 */
@Entity
@Table(
    name = "dead_letter_event",
    indexes = {
        @Index(name = "idx_dead_letter_status_created", columnList = "status, created_at"),
        @Index(name = "idx_dead_letter_workspace_created", columnList = "workspace_id, created_at DESC"),
        @Index(name = "idx_dead_letter_event_type", columnList = "event_type, created_at DESC"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {

    /**
     * Status of a dead letter event.
     */
    public enum Status {
        /** Awaiting investigation or retry */
        PENDING,
        /** Successfully reprocessed and recorded */
        RESOLVED,
        /** Manually marked as unrecoverable (e.g., invalid data) */
        DISCARDED,
    }

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /** The workspace this event was intended for */
    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** The event type that failed to record */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private ActivityEventType eventType;

    /** When the original event occurred */
    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** The actor ID who performed the action (nullable for system events) */
    @Column(name = "actor_id")
    private Long actorId;

    /** The repository ID where the event occurred */
    @Column(name = "repository_id")
    private Long repositoryId;

    /** What was acted on: pull_request, issue, review, etc. */
    @Column(name = "target_type", length = 32)
    private String targetType;

    /** ID of the target object */
    @Column(name = "target_id")
    private Long targetId;

    /** XP that would have been awarded */
    @Column(name = "xp", nullable = false)
    private double xp;

    /** Source of this event: github, mentor, notification, system */
    @NotNull
    @Column(name = "source_system", length = 32, nullable = false)
    private String sourceSystem;

    /** Original event payload */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** The error message from the last failure */
    @NotNull
    @Column(name = "error_message", length = 2000, nullable = false)
    private String errorMessage;

    /** Full exception class name for categorization */
    @Column(name = "error_type", length = 256)
    private String errorType;

    /** Stack trace for debugging (truncated to avoid bloat) */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /** Number of retry attempts before giving up */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Current status of this dead letter */
    @Setter
    @Builder.Default
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status = Status.PENDING;

    /** When this dead letter was created */
    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When this dead letter was last updated (e.g., status change) */
    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** When this dead letter was resolved/discarded */
    @Setter
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Notes from operator about resolution (e.g., "Data corruption - discarded") */
    @Setter
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Mark this dead letter as resolved after successful reprocessing.
     *
     * @param notes optional notes about the resolution
     */
    public void markResolved(String notes) {
        this.status = Status.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
    }

    /**
     * Mark this dead letter as discarded (unrecoverable).
     *
     * @param reason explanation of why this event cannot be recovered
     */
    public void markDiscarded(String reason) {
        this.status = Status.DISCARDED;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = reason;
    }

    /**
     * Increment the retry count after a failed retry attempt.
     *
     * @return the new retry count
     */
    public int incrementRetryCount() {
        return ++this.retryCount;
    }
}
