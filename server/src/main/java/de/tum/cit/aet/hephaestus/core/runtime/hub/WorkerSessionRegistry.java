package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.ForceReconnect;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.socket.CloseStatus;

/**
 * Holds at most one {@link WorkerSession} per {@code workerId}. Reconnect from the same worker
 * atomically evicts the older connection so the dispatcher cannot briefly see double capacity.
 * Implements {@link SmartLifecycle} so graceful shutdown closes every session before the embedded
 * web server stops accepting traffic.
 */
public class WorkerSessionRegistry implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerSessionRegistry.class);

    private final ConcurrentHashMap<String, WorkerSession> byWorkerId = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private volatile boolean running = true;

    public WorkerSessionRegistry(ApplicationEventPublisher events, MeterRegistry meterRegistry) {
        this.events = events;
        Gauge.builder("worker.hub.sessions.active", byWorkerId, ConcurrentHashMap::size)
            .description("Active worker WSS connections registered on this app-pod")
            .register(meterRegistry);
    }

    /**
     * Atomically register {@code session}; if a previous {@link WorkerSession} exists for the
     * same {@code workerId}, the loser is closed with {@link CloseStatus#NORMAL} and a
     * {@link WorkerDisconnectedEvent} is published. The returned session is always the one in
     * the map (never the evicted loser).
     */
    public WorkerSession register(WorkerSession incoming) {
        AtomicReference<WorkerSession> evicted = new AtomicReference<>();
        WorkerSession result = byWorkerId.compute(incoming.workerId(), (id, existing) -> {
            if (existing != null && existing != incoming) {
                evicted.set(existing);
            }
            return incoming;
        });

        WorkerSession loser = evicted.get();
        if (loser != null) {
            log.info(
                "Evicting duplicate worker session: workerId={}, oldSession={}, newSession={}",
                incoming.workerId(),
                loser.sessionId(),
                incoming.sessionId()
            );
            // Close after swap so any in-flight forwarder reading byWorkerId can't pick the loser.
            loser.close(CloseStatus.NORMAL);
            events.publishEvent(
                new WorkerDisconnectedEvent(incoming.workerId(), loser.sessionId(), "duplicate-evicted", Instant.now())
            );
        }
        return result;
    }

    /**
     * Remove the session identified by both {@code workerId} AND object identity — guards
     * against a stale handler removing a fresh session installed by a reconnect.
     */
    public void unregister(WorkerSession session, String reason) {
        boolean removed = byWorkerId.remove(session.workerId(), session);
        if (removed) {
            events.publishEvent(
                new WorkerDisconnectedEvent(session.workerId(), session.sessionId(), reason, Instant.now())
            );
        }
    }

    public Optional<WorkerSession> findByWorkerId(String workerId) {
        return Optional.ofNullable(byWorkerId.get(workerId));
    }

    /** Snapshot of currently registered sessions; safe to iterate without external locking. */
    public Collection<WorkerSession> sessions() {
        return Collections.unmodifiableCollection(byWorkerId.values());
    }

    public int activeCount() {
        return byWorkerId.size();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        for (WorkerSession session : byWorkerId.values()) {
            session.send(new ForceReconnect("hub draining"));
            session.close(CloseStatus.GOING_AWAY);
            events.publishEvent(
                new WorkerDisconnectedEvent(session.workerId(), session.sessionId(), "hub-draining", Instant.now())
            );
        }
        byWorkerId.clear();
        log.info("WorkerSessionRegistry drained on shutdown");
    }

    @Override
    public int getPhase() {
        // SmartLifecycle stops in DESCENDING phase order — higher phase stops first. A phase
        // greater than WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE makes the WS
        // registry drain BEFORE the embedded server stops accepting traffic.
        return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE + 1;
    }
}
