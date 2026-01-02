package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
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
 *
 * <h3>Indexes</h3>
 * <p>Optimized for leaderboard and activity queries:
 * <ul>
 *   <li>workspace_id + occurred_at: Leaderboard aggregations with time range</li>
 *   <li>actor_id + occurred_at: User activity feed (mentor context)</li>
 *   <li>workspace_id + actor_id + occurred_at: Combined workspace + user queries</li>
 *   <li>correlation_id: Email attribution chain lookups</li>
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
        @Index(name = "idx_activity_event_workspace_actor_occurred", columnList = "workspace_id, actor_id, occurred_at DESC"),
        // Attribution queries: correlation chain lookups
        @Index(name = "idx_activity_event_correlation", columnList = "correlation_id"),
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

    /** Everything else goes here - ONLY for truly variable metadata (titles, paths, etc.) */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** When we persisted to the database */
    @NotNull
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    /**
     * Schema version for forward compatibility and event evolution.
     *
     * <p><strong>When to increment:</strong>
     * <ul>
     *   <li>Event structure changes (new required fields, field renames)</li>
     *   <li>XP calculation formula changes (to distinguish old vs new scoring)</li>
     *   <li>Payload format changes (new required keys, changed semantics)</li>
     * </ul>
     *
     * <p><strong>Migration pattern:</strong> Use {@link ActivityEventService#migrateToCurrentVersion}
     * to upgrade events from older schema versions when processing historical data.
     *
     * @see ActivityEventService#CURRENT_SCHEMA_VERSION
     * @see ActivityEventService#migrateToCurrentVersion(ActivityEvent)
     */
    @Builder.Default
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    /**
     * XP formula version used for this event.
     *
     * <p>This tracks which version of the XP calculation formula was used
     * when computing the XP value. This enables:
     * <ul>
     *   <li>Historical accuracy: Explain why old events have specific XP</li>
     *   <li>Audit trail: Track formula changes over time</li>
     *   <li>Recalculation: Optionally recompute XP with new formula</li>
     * </ul>
     *
     * <p>Formula versions:
     * <ul>
     *   <li>1 = Original harmonic mean formula</li>
     *   <li>2 = Future updates (document changes here)</li>
     * </ul>
     */
    @Builder.Default
    @Column(name = "formula_version", nullable = false)
    private int formulaVersion = 1;

    /**
     * SHA-256 hash of event content for tamper detection.
     *
     * <p>Computed from: eventKey + eventType + occurredAt + targetId + xp + schemaVersion.
     * This provides an audit trail integrity guarantee - any modification to
     * event content would invalidate the hash.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (ingestedAt == null) {
            ingestedAt = Instant.now();
        }
        if (contentHash == null) {
            contentHash = computeContentHash();
        }
    }

    /**
     * Compute SHA-256 hash of event content for integrity verification.
     *
     * @return hex-encoded SHA-256 hash of event content
     */
    private String computeContentHash() {
        String content = String.format(
            "%s|%s|%s|%s|%.2f|%d|%d",
            eventKey,
            eventType != null ? eventType.getValue() : "",
            occurredAt != null ? occurredAt.toEpochMilli() : 0,
            targetId != null ? targetId : 0,
            xp,
            schemaVersion,
            formulaVersion
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verify the integrity of this event by comparing stored hash with computed hash.
     *
     * @return true if the event content has not been tampered with
     */
    public boolean verifyIntegrity() {
        if (contentHash == null) {
            return false;
        }
        return contentHash.equals(computeContentHash());
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
}
