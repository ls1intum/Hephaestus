package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared timestamp inspector that enforces {@code hephaestus.mentor.stdin-write-timeout-ms} across
 * all active sessions.
 *
 * <p>Registered as a Spring bean and ticked by a {@code @Scheduled} method (see
 * {@code InteractiveSandboxRegistry#tickWatchdog}). Iterates registered sessions, asks each
 * whether its writer has a stalled write, and if so calls {@code session.handleStdinTimeout()} —
 * which in turn marks the writer terminal and calls {@code process.destroyForcibly()} on the
 * exec subprocess to unblock the pipe write.
 *
 * <p>The work per tick is O(N) timestamp reads + zero blocking, so one shared schedule across all
 * sessions is appropriate.
 */
public final class StdinWriteWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StdinWriteWatchdog.class);

    private final ConcurrentHashMap<UUID, StallTarget> targets = new ConcurrentHashMap<>();

    /** Register a session for watchdog supervision. Idempotent. */
    public void register(UUID sessionId, StallTarget target) {
        targets.put(sessionId, target);
    }

    /** Unregister a session (call from session close path). Idempotent. */
    public void unregister(UUID sessionId) {
        targets.remove(sessionId);
    }

    /** Sweep: for each registered session, if its write is stalled, invoke its timeout handler. */
    public void tick() {
        long now = System.nanoTime();
        for (var entry : targets.entrySet()) {
            StallTarget t = entry.getValue();
            try {
                if (t.writeStalled(now)) {
                    log.warn("Stdin write stalled — triggering destroyForcibly: sessionId={}", entry.getKey());
                    t.onWriteTimeout();
                }
            } catch (Throwable th) {
                log.warn("Watchdog tick failed for session {}: {}", entry.getKey(), th.toString());
            }
        }
    }

    public int activeTargets() {
        return targets.size();
    }

    /** Probe + action for one supervised session. */
    public interface StallTarget {
        /** @return true iff there is currently a write in flight older than the configured threshold. */
        boolean writeStalled(long nowNanos);

        /** Invoked when {@link #writeStalled} returned true. Must be idempotent and non-blocking. */
        void onWriteTimeout();
    }

    /** Test seam — returns true if a session is registered. */
    public boolean isRegistered(UUID sessionId) {
        return targets.containsKey(sessionId);
    }
}
