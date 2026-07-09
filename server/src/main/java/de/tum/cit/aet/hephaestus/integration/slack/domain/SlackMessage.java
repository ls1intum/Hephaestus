package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * An ingested Slack message — the PII-bearing content surface. Stores rendered {@code text} only (data
 * minimization: no block payloads, reactions, or files). Workspace-scoped (scalar {@code workspaceId}), idempotent
 * on {@code (workspaceId, slackChannelId, slackTs)}. {@code editedAt}/{@code deletedAt} carry Slack
 * {@code message_changed}/{@code message_deleted} tombstones (GDPR Art. 17); {@code idx_slack_message_ingest}
 * drives the bounded-retention sweep.
 */
@Entity
@Table(
    name = "slack_message",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_slack_message",
        columnNames = { "workspace_id", "slack_channel_id", "slack_ts" }
    ),
    indexes = @Index(name = "idx_slack_message_ingest", columnList = "workspace_id, ingested_at")
)
@Getter
@Setter
@NoArgsConstructor
public class SlackMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "slack_team_id", nullable = false, length = 32)
    private String slackTeamId;

    @Column(name = "slack_channel_id", nullable = false, length = 32)
    private String slackChannelId;

    @Column(name = "slack_ts", nullable = false, length = 32)
    private String slackTs;

    @Column(name = "slack_thread_ts", length = 32)
    private @Nullable String slackThreadTs;

    @Column(name = "author_slack_user_id", length = 32)
    private @Nullable String authorSlackUserId;

    /**
     * The resolved workspace {@code User} id of the author (the same id space as
     * {@code MentorChatRequest#developerId}), or {@code null} when the Slack sender is not a linked, workspace-member
     * developer. This is the firewall stamp the conversation projector unions into {@code slack_thread}'s
     * {@code participant_member_ids}. Written via the native ingest insert.
     */
    @Column(name = "author_member_id")
    private @Nullable Long authorMemberId;

    @Column(name = "text", columnDefinition = "TEXT")
    private @Nullable String text;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;
}
