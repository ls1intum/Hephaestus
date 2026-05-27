package de.tum.cit.aet.hephaestus.integration.slack.refs;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Persisted Slack message — channel context for practice detection and the mentor runtime.
 *
 * <p>Workspace-scoped through {@link Connection#getWorkspace()}. The (connection_id,
 * team_id, channel_id, ts) tuple is the natural key — {@code ts} is Slack's strictly
 * monotonically increasing message identifier per channel and uniquely identifies a
 * message within that channel; pairing it with team + connection prevents two installs
 * of the same Slack workspace from colliding.
 *
 * <h2>ToS compliance contract</h2>
 * <ul>
 *   <li><b>Tenant boundary:</b> rows only ever joined back through {@code connection_id};
 *       no cross-workspace queries exist (enforced by {@code DataIsolationArchitectureTest}).</li>
 *   <li><b>Deletion events:</b> {@code message_deleted} → {@link #softDelete(Instant)}
 *       wired by {@code SlackMessageDeletionHandler}; {@code channel_deleted} cascades
 *       via the {@code slack_channel} FK; {@code app_uninstalled}/{@code tokens_revoked}
 *       cascade via the {@code connection} FK ({@code ON DELETE CASCADE}).</li>
 *   <li><b>No aggregation:</b> rows never leave the workspace-scoped query paths.</li>
 *   <li><b>Encryption at rest:</b> covered by the underlying volume; no per-column
 *       encryption is necessary — content is already restricted to the workspace tenancy.</li>
 * </ul>
 *
 * <p>Soft delete (via {@code deleted_at}) rather than physical delete is intentional:
 * a {@code message_deleted} event must hide the message from queries but must not
 * remove the audit trail that the message ever existed. Hard deletion happens only on
 * cascade from {@link Connection} or {@link SlackChannel} removal.
 */
@Entity
@Table(
    name = "slack_message",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_slack_message",
        columnNames = { "connection_id", "team_id", "channel_id", "ts" }
    ),
    indexes = {
        // O(log n) thread fetch: SELECT WHERE connection_id=? AND channel_id=? ORDER BY ts DESC
        @Index(name = "ix_slack_message_thread", columnList = "connection_id, channel_id, ts DESC"),
    }
)
public class SlackMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "team_id", nullable = false, length = 64)
    private String teamId;

    @Column(name = "channel_id", nullable = false, length = 64)
    private String channelId;

    @Column(name = "ts", nullable = false, length = 64)
    private String ts;

    @Column(name = "thread_ts", length = 64)
    @Nullable
    private String threadTs;

    @Column(name = "user_id", length = 64)
    @Nullable
    private String userId;

    @Column(name = "text", columnDefinition = "text")
    @Nullable
    private String text;

    @Column(name = "blocks", columnDefinition = "jsonb")
    @Nullable
    private String blocks;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    protected SlackMessage() {}

    public SlackMessage(Connection connection, String teamId, String channelId, String ts) {
        this.connection = connection;
        this.teamId = teamId;
        this.channelId = channelId;
        this.ts = ts;
    }

    public Long getId() {
        return id;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getTs() {
        return ts;
    }

    @Nullable
    public String getThreadTs() {
        return threadTs;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getBlocks() {
        return blocks;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    @Nullable
    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setThreadTs(@Nullable String threadTs) {
        this.threadTs = threadTs;
    }

    public void setUserId(@Nullable String userId) {
        this.userId = userId;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public void setBlocks(@Nullable String blocks) {
        this.blocks = blocks;
    }

    /**
     * Mark a message tombstoned because Slack delivered a {@code message_deleted} event.
     * Idempotent — repeated soft-deletes preserve the original tombstone timestamp so a
     * replayed webhook does NOT shift the deletion audit trail.
     */
    public void softDelete(Instant at) {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = at;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
