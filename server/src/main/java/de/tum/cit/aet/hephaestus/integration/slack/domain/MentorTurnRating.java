package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * A member's binary thumb ({@link TurnRating}) on one mentor turn, captured from the feedback buttons attached to
 * the streamed reply. Workspace-scoped (scalar {@code workspaceId} → a tenancy predicate rides every query;
 * the table is deliberately kept out of {@code WorkspaceScopedTables.GLOBAL_TABLES}).
 *
 * <p><strong>Append-only.</strong> {@code @Immutable}: each click writes a fresh row. The newest row per
 * {@code (rater, turn)} is the current rating — "latest wins" is a read ordering, never an in-place update — which
 * keeps the full temporal record for research without a mutable state column.
 */
@Entity
@Immutable
@Table(
    name = "mentor_turn_rating",
    indexes = { @Index(name = "idx_mentor_turn_rating_rater_created", columnList = "rater_user_id, created_at DESC") }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorTurnRating {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** The workspace {@code User} (member) id of the rater — the same firewall-stamped id the projector matches. */
    @Column(name = "rater_user_id", nullable = false)
    private Long raterUserId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "thread_ts", length = 32)
    private @Nullable String threadTs;

    /** The {@code ts} of the mentor reply the thumb was attached to (the button value carries it). */
    @Column(name = "slack_message_ts", nullable = false, length = 32)
    private String slackMessageTs;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 16)
    private TurnRating rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
