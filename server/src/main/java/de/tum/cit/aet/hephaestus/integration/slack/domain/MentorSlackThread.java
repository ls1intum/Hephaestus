package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * Maps a Slack DM to the mentor {@code chat_thread} it drives (P2/D7). Workspace-scoped (scalar
 * {@code workspaceId}); {@code chatThreadId} is a scalar cross-module reference (never a JPA association) into the
 * {@code agent}/{@code mentor} module — the {@code chat_thread} row itself is created inside that module via the
 * {@code mentor-chat} named interface. A DB-level FK (declared in the changelog) still enforces referential
 * integrity + {@code ON DELETE CASCADE}.
 */
@Entity
@Table(
    name = "mentor_slack_thread",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mentor_slack_thread",
        columnNames = { "workspace_id", "slack_channel_id" }
    )
)
@Getter
@Setter
@NoArgsConstructor
public class MentorSlackThread {

    /** Client-assigned UUID (no DB default), mirroring the mentor module's {@code chat_thread} id convention. */
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "chat_thread_id", nullable = false, columnDefinition = "UUID")
    private UUID chatThreadId;

    @Column(name = "slack_team_id", nullable = false, length = 32)
    private String slackTeamId;

    @Column(name = "slack_channel_id", nullable = false, length = 32)
    private String slackChannelId;

    @Column(name = "slack_thread_ts", length = 32)
    private @Nullable String slackThreadTs;

    @Column(name = "slack_user_id", nullable = false, length = 32)
    private String slackUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
