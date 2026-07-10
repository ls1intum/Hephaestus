package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A Slack thread aggregate (root message + its replies) keyed by {@code slack_thread_ts}. Workspace-scoped
 * (scalar {@code workspaceId}); loosely coupled to {@link SlackMessage} by {@code (slackChannelId, slackThreadTs)}
 * rather than a hard FK, so message ingestion and thread bookkeeping stay independently idempotent.
 */
@Entity
@Table(
    name = "slack_thread",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_slack_thread",
        columnNames = { "workspace_id", "slack_channel_id", "slack_thread_ts" }
    )
)
@Getter
@Setter
@NoArgsConstructor
public class SlackThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "slack_channel_id", nullable = false, length = 32)
    private String slackChannelId;

    @Column(name = "slack_thread_ts", nullable = false, length = 32)
    private String slackThreadTs;

    @Column(name = "first_ts", length = 32)
    private @Nullable String firstTs;

    @Column(name = "last_ts", length = 32)
    private @Nullable String lastTs;

    @Column(name = "message_count", nullable = false)
    @ColumnDefault("0")
    private int messageCount = 0;

    /**
     * Conversation-detection watermark: the Slack {@code ts} through which this thread was last analysed
     * (changelog {@code 1782980500800-13}). Read/written by {@code integration.slack.conversation} through the
     * scoped {@code SlackThreadRepository} JPQL (advance in
     * {@code SlackThreadRepository#advanceReviewWatermark}, compare in
     * {@code SlackThreadRepository#findSettledCandidateRows}) — a plain mapped field, no raw SQL involved.
     */
    @Column(name = "last_reviewed_ts", length = 32)
    private @Nullable String lastReviewedTs;

    /**
     * Resolved participant member ids for the mentor-context participant firewall ({@code bigint[]}, changelog
     * {@code 1782980500800-12}; a GIN index backs the {@code = ANY(...)} membership lookup). Selected as a plain
     * mapped field via Hibernate's {@code @JdbcTypeCode(SqlTypes.ARRAY)} marshalling wherever the whole array is
     * read (e.g. {@code SlackThreadRepository#findSettledCandidateRows}); the {@code = ANY(...)} membership test
     * itself stays a native {@code @Query} on {@code SlackThreadRepository#findParticipatingThreadRows} because
     * Postgres array-membership has no portable JPQL form. {@code NOT NULL DEFAULT '{}'} — never null, empty until
     * participants resolve.
     */
    @Column(name = "participant_member_ids", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @ColumnDefault("'{}'::bigint[]")
    private long[] participantMemberIds = new long[0];

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
