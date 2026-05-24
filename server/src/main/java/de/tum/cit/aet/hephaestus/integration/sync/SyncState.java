package de.tum.cit.aet.hephaestus.integration.sync;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

/**
 * Universal per-stream sync state — one row per (connection, stream_name).
 *
 * <p>Collapses ~7 scattered per-entity cursor columns (issues_synced_at, prs_synced_at,
 * …) + ~30 imperative {@code BackfillStateProvider} methods into one Airbyte-style
 * state-as-data record. The {@code cursor} column carries a serialized
 * {@code SyncMessage.Cursor} (Opaque / Numbered / Watermark).
 */
@Entity
@Table(
    name = "sync_state",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sync_state",
        columnNames = {"connection_id", "stream_name"}
    )
)
public class SyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Column(name = "stream_name", nullable = false, length = 128)
    private String streamName;

    /** Serialized {@code SyncMessage.Cursor} (sealed). */
    @Column(name = "cursor", columnDefinition = "jsonb", nullable = false)
    private String cursor = "{}";

    @Column(name = "last_synced_at")
    @Nullable
    private Instant lastSyncedAt;

    @Column(name = "last_realtime_at")
    @Nullable
    private Instant lastRealtimeAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {
    }

    public SyncState(Connection connection, String streamName) {
        this.connection = connection;
        this.streamName = streamName;
    }

    public Long getId() { return id; }
    public Connection getConnection() { return connection; }
    public String getStreamName() { return streamName; }
    public String getCursor() { return cursor; }
    @Nullable public Instant getLastSyncedAt() { return lastSyncedAt; }
    @Nullable public Instant getLastRealtimeAt() { return lastRealtimeAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCursor(String cursor) { this.cursor = cursor == null ? "{}" : cursor; }
    public void setLastSyncedAt(@Nullable Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public void setLastRealtimeAt(@Nullable Instant lastRealtimeAt) { this.lastRealtimeAt = lastRealtimeAt; }
}
