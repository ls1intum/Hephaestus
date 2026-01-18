package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Minimal immutable activity event - the Stripe/Square approach.
 *
 * <p>One table. Minimal columns. No OCEL/CloudEvents/ActivityStreams complexity.
 *
 * <p>Supports:
 * <ul>
 *   <li>Leaderboard: COUNT(*) GROUP BY actor_id, event_type</li>
 *   <li>Mentor context: SELECT * WHERE actor_id = ? ORDER BY occurred_at DESC</li>
 *   <li>DX Core 4: Maps to Speed (lead time), Effectiveness (review cycle), Quality metrics</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <p>Deduplicated by {@code (workspace_id, event_key)}.
 *
 * <h3>Indexes</h3>
 * <p>Optimized for leaderboard and activity queries:
 * <ul>
 *   <li>workspace_id + occurred_at: Leaderboard aggregations with time range</li>
 *   <li>actor_id + occurred_at: User activity feed (mentor context)</li>
 *   <li>workspace_id + actor_id + occurred_at: Combined workspace + user queries</li>
 *   <li>workspace_id + target_type + target_id: XP lookup for profile hydration</li>
 * </ul>
 *
 * <h3>Immutability</h3>
 * <p>Annotated with {@code @Immutable} to prevent accidental updates at the Hibernate level.
 * Activity events are append-only; corrections are made by recording new events.
 */
@Entity
@Immutable
@Table(
    name = "activity_event",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_event_workspace_key", columnNames = { "workspace_id", "event_key" }),
    },
    indexes = {
        // Leaderboard queries: workspace aggregations with time range
        @Index(name = "idx_activity_event_workspace_occurred", columnList = "workspace_id, occurred_at DESC"),
        // User activity queries: mentor context with time range
        @Index(name = "idx_activity_event_actor_occurred", columnList = "actor_id, occurred_at DESC"),
        // Combined workspace + user queries: activity breakdown
        @Index(
            name = "idx_activity_event_workspace_actor_occurred",
            columnList = "workspace_id, actor_id, occurred_at DESC"
        ),
        // XP lookup for profile hydration: batch load XP by target
        @Index(name = "idx_activity_event_xp_lookup", columnList = "workspace_id, target_type, target_id"),
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

    /**
     * XP points earned for this activity (computed at event time).
     *
     * <p><strong>Precision policy:</strong> Values are rounded to 2 decimal places
     * using HALF_UP rounding before storage. This ensures consistent aggregation
     * and fair leaderboard scoring.
     *
     * <p><strong>Validation:</strong> Enforced non-negative via {@code @PositiveOrZero}
     * and database CHECK constraint. Negative XP is only allowed for correction
     * events (e.g., {@code REVIEW_DISMISSED}) which are handled specially.
     *
     * @see de.tum.in.www1.hephaestus.activity.scoring.XpPrecision
     */
    @Builder.Default
    @PositiveOrZero
    @Column(name = "xp", nullable = false)
    private double xp = 0.0;

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

    /**
     * Build a deterministic event key for idempotent upserts.
     *
     * <p>The key format is {@code {event_type}:{target_id}:{timestamp_ms}}.
     * This ensures that duplicate events (same action on same entity at same time)
     * are deduplicated at the database level via unique constraint.
     *
     * @param type the event type (e.g., PULL_REQUEST_OPENED)
     * @param targetId the ID of the target entity
     * @param timestamp when the event occurred
     * @return a deterministic key for idempotency checking
     */
    public static String buildKey(ActivityEventType type, Long targetId, Instant timestamp) {
        return String.format("%s:%d:%d", type.getValue(), targetId, timestamp.toEpochMilli());
    }

    /**
     * Verify the integrity of this activity event.
     *
     * <p><strong>Note:</strong> Content hash verification was removed as part of schema
     * simplification (content_hash column dropped). This method is a stub that always
     * returns true until integrity verification is re-implemented if needed.
     *
     * @return true (integrity verification not currently implemented)
     */
    public boolean verifyIntegrity() {
        // Content hash verification was removed - see changelog 1768900000000-5
        // Always return true until re-implemented
        return true;
    }
}
