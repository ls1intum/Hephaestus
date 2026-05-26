package de.tum.cit.aet.hephaestus.integration.slack.refs;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Persisted Slack channel metadata.
 *
 * <p>Workspace-scoped through {@link Connection#getWorkspace()}. Slack ToS permits this
 * workspace-internal persistence so long as: (a) data never crosses the
 * tenant boundary, (b) {@code channel_deleted}/{@code app_uninstalled}/
 * {@code tokens_revoked} events purge the corresponding rows (cascade from
 * {@link Connection} handles the last two; {@code channel_deleted} is wired in
 * {@code SlackLifecycleListener.onScopeChanged}), and (c) no cross-tenant
 * aggregation or model training. AES-GCM at-rest covers (d).
 *
 * <p>Companion to {@link SlackMessage}; deletion of a channel cascades to its
 * messages at the database level (FK with {@code ON DELETE CASCADE} configured
 * on {@code slack_message.channel_id} via Liquibase changeset
 * {@code 1779700400000_slack_persistence.xml}).
 */
@Entity
@Table(
    name = "slack_channel",
    uniqueConstraints = @UniqueConstraint(name = "uq_slack_channel", columnNames = { "connection_id", "channel_id" }),
    indexes = { @Index(name = "ix_slack_channel_connection", columnList = "connection_id") }
)
public class SlackChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "channel_id", nullable = false, length = 64)
    private String channelId;

    @Column(name = "name", length = 256)
    @Nullable
    private String name;

    @Column(name = "topic", columnDefinition = "text")
    @Nullable
    private String topic;

    @Column(name = "purpose", columnDefinition = "text")
    @Nullable
    private String purpose;

    @Column(name = "is_archived", nullable = false)
    @ColumnDefault("false")
    private boolean isArchived = false;

    @Column(name = "archived_at")
    @Nullable
    private Instant archivedAt;

    @Column(name = "member_count")
    @Nullable
    private Integer memberCount;

    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    protected SlackChannel() {}

    public SlackChannel(Connection connection, String channelId, @Nullable String name) {
        this.connection = connection;
        this.channelId = channelId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getChannelId() {
        return channelId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getTopic() {
        return topic;
    }

    @Nullable
    public String getPurpose() {
        return purpose;
    }

    public boolean isArchived() {
        return isArchived;
    }

    @Nullable
    public Instant getArchivedAt() {
        return archivedAt;
    }

    @Nullable
    public Integer getMemberCount() {
        return memberCount;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public void setTopic(@Nullable String topic) {
        this.topic = topic;
    }

    public void setPurpose(@Nullable String purpose) {
        this.purpose = purpose;
    }

    public void setMemberCount(@Nullable Integer memberCount) {
        this.memberCount = memberCount;
    }

    /**
     * Mark the channel archived. Idempotent — calling on an already-archived row preserves
     * the original {@code archivedAt} timestamp (do not flap on webhook redelivery).
     */
    public void archive(Instant at) {
        if (this.isArchived) {
            return;
        }
        this.isArchived = true;
        this.archivedAt = at;
    }

    public void unarchive() {
        this.isArchived = false;
        this.archivedAt = null;
    }
}
