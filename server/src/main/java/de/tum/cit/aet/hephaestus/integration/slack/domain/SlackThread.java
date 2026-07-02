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
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * A Slack thread aggregate (root message + its replies) keyed by {@code slack_thread_ts} (P3). Workspace-scoped
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
    private int messageCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
