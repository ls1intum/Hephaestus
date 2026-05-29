package de.tum.cit.aet.hephaestus.agent.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Self-reported worker liveness for multi-replica orphan recovery (#1138). Each worker upserts its
 * own row and refreshes {@code last_heartbeat} on a timer, so staleness reflects the JVM that
 * actually executes jobs — not the WSS control channel, which can drop while a worker keeps running.
 * A worker whose heartbeat goes stale has its in-flight jobs requeued by {@link AgentJobZombieSweeper}.
 */
@Entity
@Table(name = "worker_registry")
@Getter
@Setter
@NoArgsConstructor
public class WorkerRegistry {

    /** Stable worker identity (configured {@code workerId} or container hostname). */
    @Id
    @Column(name = "worker_id", length = 255)
    private String workerId;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
}
