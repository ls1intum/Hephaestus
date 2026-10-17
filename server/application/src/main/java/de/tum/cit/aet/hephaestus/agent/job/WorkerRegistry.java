package de.tum.cit.aet.hephaestus.agent.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Each worker writes its own heartbeat so staleness identifies the JVM that owns the execution.
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
