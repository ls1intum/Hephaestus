package de.tum.cit.aet.hephaestus.integration.core.sync.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One row per {@code Connection}: the webhook-liveness ("last event processed") watermark.
 *
 * <p>Deliberately NOT an audit trail — no counters, no history, just the most recent event's
 * timestamp and type. Written by {@link ConnectionActivityRecorder} from the NATS consumer's
 * post-dispatch hook, throttled to at most one write per connection per 15s.
 *
 * <p>{@code connection_id} is the primary key (not a generated surrogate) — this is a 1:1
 * extension of {@code Connection}, not an independent entity with its own identity.
 */
@Entity
@Table(name = "connection_activity")
public class ConnectionActivity {

    @Id
    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "last_event_at")
    @Nullable
    private Instant lastEventAt;

    @Column(name = "last_event_type", length = 128)
    @Nullable
    private String lastEventType;

    protected ConnectionActivity() {}

    public ConnectionActivity(Long connectionId, Long workspaceId, Instant lastEventAt, String lastEventType) {
        this.connectionId = connectionId;
        this.workspaceId = workspaceId;
        this.lastEventAt = lastEventAt;
        this.lastEventType = lastEventType;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    @Nullable
    public Instant getLastEventAt() {
        return lastEventAt;
    }

    @Nullable
    public String getLastEventType() {
        return lastEventType;
    }
}
