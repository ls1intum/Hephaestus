package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
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
import org.hibernate.annotations.Type;

/**
 * Minimal immutable activity event - the Stripe/Square approach.
 *
 * <p>One table. 11 columns. No OCEL/CloudEvents/ActivityStreams complexity.
 *
 * <p>Supports:
 * <ul>
 *   <li>Leaderboard: COUNT(*) GROUP BY actor_id, event_type</li>
 *   <li>Mentor context: SELECT * WHERE actor_id = ? ORDER BY occurred_at DESC</li>
 *   <li>Email attribution: WHERE correlation_id = ? for notification→click→action chains</li>
 *   <li>DX Core 4: Maps to Speed (lead time), Effectiveness (review cycle), Quality metrics</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>Deduplicated by {@code (workspace_id, event_key)}.
 */
@Entity
@Table(
    name = "activity_event",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_event_workspace_key", columnNames = { "workspace_id", "event_key" }),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /** Deterministic key for idempotent upserts: {type}:{target_id}:{timestamp_ms} */
    @NotNull
    @Column(name = "event_key", nullable = false)
    private String eventKey;

    /** What happened: pr.opened, review.submitted, bad_practice.detected, etc. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private ActivityEventType eventType;

    /** When the event actually happened (source timestamp) */
    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** The user who performed the action (nullable for system events) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    /** What was acted on: pull_request, issue, review, notification, etc. */
    @Column(name = "target_type", length = 32)
    private String targetType;

    /** ID of the target object */
    @Column(name = "target_id")
    private Long targetId;

    /** Source of this event: github, mentor, notification, system */
    @NotNull
    @Column(name = "source_system", length = 32, nullable = false)
    private String sourceSystem;

    /** Groups related events: notification.sent → notification.clicked → pr.opened */
    @Column(name = "correlation_id")
    private UUID correlationId;

    /** XP points earned for this activity (computed at event time) */
    @Builder.Default
    @Column(name = "xp", nullable = false)
    private double xp = 0.0;

    /** Everything else goes here - ONLY for truly variable metadata (titles, paths, etc.) */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** When we persisted to the database */
    @NotNull
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (ingestedAt == null) {
            ingestedAt = Instant.now();
        }
    }

    /** Build event key for idempotency */
    public static String buildKey(ActivityEventType type, Long targetId, Instant timestamp) {
        return String.format("%s:%d:%d", type.getValue(), targetId, timestamp.toEpochMilli());
    }
}
