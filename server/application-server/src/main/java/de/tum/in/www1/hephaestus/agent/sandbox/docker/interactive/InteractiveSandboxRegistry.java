package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.DockerOperations;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxContainerManager;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxLabels;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandboxState;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * In-memory registry of live {@link DockerAttachedSandboxAdapter} sessions keyed by
 * {@code (userId, workspaceId)}.
 *
 * <p>Owns four cross-cutting concerns that don't fit on a single session: capacity caps (DOS
 * guard), idle reaper, stdin-write watchdog tick, and post-restart orphan sweep. State is
 * in-process — see #1077 for the eventual multi-replica story.
 */
@WorkspaceAgnostic("Interactive sandbox registry keys by user+workspace, not by workspace-iteration semantics")
public class InteractiveSandboxRegistry {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxRegistry.class);

    private final InteractiveSandboxProperties properties;
    private final SandboxContainerManager containerManager;
    private final InteractiveSandboxMetrics metrics;
    private final StdinWriteWatchdog watchdog;

    private final ConcurrentHashMap<SessionKey, DockerAttachedSandboxAdapter> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sessionsPerUser = new ConcurrentHashMap<>();

    public InteractiveSandboxRegistry(
        InteractiveSandboxProperties properties,
        SandboxContainerManager containerManager,
        InteractiveSandboxMetrics metrics,
        StdinWriteWatchdog watchdog,
        MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.containerManager = containerManager;
        this.metrics = metrics;
        this.watchdog = watchdog;
        Gauge.builder("mentor.session.active", sessions, ConcurrentHashMap::size)
            .description("Currently attached mentor sandbox sessions on this replica")
            .register(meterRegistry);
        meterRegistry.gauge("mentor.watchdog.targets", Tags.empty(), watchdog, StdinWriteWatchdog::activeTargets);
    }

    /** @return the registered session for this key, or {@code null}. May be in any lifecycle state. */
    DockerAttachedSandboxAdapter find(String userId, String workspaceId) {
        return sessions.get(new SessionKey(userId, workspaceId));
    }

    /**
     * Like {@link #find} but filters out sessions that aren't {@link AttachedSandboxState#ATTACHED}.
     * The race between this check and a subsequent {@code send()} is still possible (the session
     * can transition to {@code CLOSING} a nanosecond later) — callers must accept that
     * {@code send()} after CLOSING throws cleanly. Used by {@code attach()}'s share-semantics
     * fast path.
     */
    public DockerAttachedSandboxAdapter findLive(String userId, String workspaceId) {
        DockerAttachedSandboxAdapter sandbox = sessions.get(new SessionKey(userId, workspaceId));
        return sandbox != null && sandbox.state() == AttachedSandboxState.ATTACHED ? sandbox : null;
    }

    /**
     * Atomically check capacity and register a freshly constructed sandbox.
     *
     * @return outcome; on non-{@code REGISTERED}, the caller is responsible for teardown.
     */
    public RegistrationOutcome tryRegister(DockerAttachedSandboxAdapter sandbox) {
        Objects.requireNonNull(sandbox, "sandbox");
        SessionKey key = new SessionKey(sandbox.userId(), sandbox.workspaceId());

        if (sessions.size() >= properties.maxSessionsTotal()) {
            return RegistrationOutcome.MAX_SESSIONS_TOTAL;
        }
        AtomicInteger userCount = sessionsPerUser.computeIfAbsent(sandbox.userId(), u -> new AtomicInteger());
        if (userCount.get() >= properties.maxSessionsPerUser()) {
            return RegistrationOutcome.MAX_SESSIONS_PER_USER;
        }

        if (sessions.putIfAbsent(key, sandbox) != null) {
            return RegistrationOutcome.DUPLICATE;
        }
        userCount.incrementAndGet();
        watchdog.register(sandbox.sessionId(), sandbox);
        return RegistrationOutcome.REGISTERED;
    }

    /** Callback fired by each sandbox on full close. Identity-based remove avoids races with re-register. */
    void onSandboxClosed(DockerAttachedSandboxAdapter sandbox) {
        SessionKey key = new SessionKey(sandbox.userId(), sandbox.workspaceId());
        sessions.remove(key, sandbox);
        AtomicInteger userCount = sessionsPerUser.get(sandbox.userId());
        if (userCount != null && userCount.decrementAndGet() <= 0) {
            sessionsPerUser.remove(sandbox.userId(), userCount);
        }
        watchdog.unregister(sandbox.sessionId());
    }

    /** Idle reaper. Runs on Spring's default {@code @Scheduled} executor. */
    @Scheduled(fixedDelayString = "${hephaestus.mentor.reap-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void reap() {
        if (!properties.enabled()) {
            return;
        }
        Duration ttl = Duration.ofSeconds(properties.idleTtlSeconds());
        for (DockerAttachedSandboxAdapter sandbox : sessions.values()) {
            if (sandbox.state() != AttachedSandboxState.ATTACHED) {
                continue;
            }
            if (sandbox.idleFor().compareTo(ttl) > 0) {
                log.info(
                    "Reaping idle sandbox: sessionId={}, idleFor={}s",
                    sandbox.sessionId(),
                    sandbox.idleFor().toSeconds()
                );
                sandbox.terminate(EvictionReason.IDLE);
                // Don't wait on the close here — the reaper runs on Spring's single-threaded
                // scheduler that also drives the watchdog tick. Blocking here would stall the
                // watchdog and stretch close latency under load. Cleanup completes asynchronously
                // on the sandbox's own close thread; metrics settle one tick later.
            }
        }
    }

    /** Watchdog tick. {@link StdinWriteWatchdog#tick} is non-blocking — fine to share the reaper's scheduler. */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void tickWatchdog() {
        if (!properties.enabled()) {
            return;
        }
        watchdog.tick();
    }

    /**
     * Post-restart sweep: in-memory registry is gone, so any {@code KIND=interactive} container
     * inherited from a previous run is orphan. The sync sandbox's reconciler ignores them
     * (different label key — {@code JOB_ID} vs {@code SESSION_ID}).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.enabled()) {
            return;
        }
        if (properties.replicaCount() > 1) {
            log.warn(
                "Mentor is enabled with replicaCount={}; session affinity is not enforced until #1077",
                properties.replicaCount()
            );
        }
        try {
            List<DockerOperations.ContainerInfo> managed = containerManager.listManagedContainers();
            int removed = 0;
            for (DockerOperations.ContainerInfo c : managed) {
                if (SandboxLabels.KIND_INTERACTIVE.equals(c.labels().get(SandboxLabels.KIND))) {
                    try {
                        containerManager.forceRemove(c.id());
                        removed++;
                    } catch (Exception e) {
                        log.warn("Failed to remove orphaned interactive container {}: {}", c.id(), e.getMessage());
                    }
                }
            }
            if (removed > 0) {
                log.info("Removed {} orphaned interactive container(s) on startup", removed);
            }
        } catch (Exception e) {
            log.warn("Startup interactive container sweep failed: {}", e.getMessage());
        }
    }

    /**
     * Close every live session in parallel. Serial close × {@code maxSessionsTotal} would blow
     * past Spring's per-phase shutdown timeout
     * ({@code spring.lifecycle.timeout-per-shutdown-phase}, default 30 s).
     *
     * <p>The total deadline is {@code graceTimeoutSeconds + 5 s} — the per-session
     * {@code waitForClose} budget plus a tiny scheduling slop. Operators choosing a grace larger
     * than Spring's phase timeout must coordinate both knobs.
     */
    @PreDestroy
    public void shutdown() {
        if (sessions.isEmpty()) {
            return;
        }
        int count = sessions.size();
        log.info("Closing {} mentor sandbox(es) for shutdown", count);
        Duration grace = Duration.ofSeconds(properties.graceTimeoutSeconds());
        List<DockerAttachedSandboxAdapter> snapshot = new ArrayList<>(sessions.values());
        CompletableFuture<?>[] futures = snapshot
            .stream()
            .map(sandbox ->
                CompletableFuture.runAsync(() -> {
                    try {
                        sandbox.close(grace);
                    } catch (Exception e) {
                        log.warn("Error closing sandbox during shutdown: {}", e.getMessage());
                    }
                })
            )
            .toArray(CompletableFuture[]::new);
        long deadlineSeconds = grace.toSeconds() + 5;
        try {
            CompletableFuture.allOf(futures).get(deadlineSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Parallel shutdown did not complete cleanly within {}s: {}", deadlineSeconds, e.getMessage());
        }
    }

    int size() {
        return sessions.size();
    }

    /** Registry key. */
    public record SessionKey(String userId, String workspaceId) {}

    public enum RegistrationOutcome {
        REGISTERED,
        DUPLICATE,
        MAX_SESSIONS_PER_USER,
        MAX_SESSIONS_TOTAL,
    }
}
