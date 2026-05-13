package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandboxState;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.Disposable;

/**
 * Docker-backed {@link AttachedSandbox}: owns the {@link PiProcessHandle}, JSONL pump, JSONL
 * writer, frame ring buffer, and per-subscriber fan-out for one session.
 *
 * <p>State transitions are forward-only: {@code ATTACHED → CLOSING → CLOSED}. Every transition
 * is CAS-guarded; the loser observes the winner's eviction reason and waits on
 * {@link #closed}. {@code close(grace)} and {@code terminate(reason)} are the two entry points,
 * differing only in their default reason and grace value.
 *
 * <p>The close sequence stops the container with the caller-supplied grace, drains the exec
 * subprocess with a bounded {@code waitFor} (no FD leak on a hung docker exec), tears down
 * resources, completes {@link #closed}, and invokes the registry callback that decrements caps.
 */
public final class DockerAttachedSandboxAdapter implements AttachedSandbox, StdinWriteWatchdog.StallTarget {

    private static final Logger log = LoggerFactory.getLogger(DockerAttachedSandboxAdapter.class);

    private static final String MDC_SESSION_ID = "mentor.sessionId";
    private static final String MDC_CONTAINER_ID = "mentor.containerId";

    private final UUID sessionId;
    private final String userId;
    private final String workspaceId;
    private final String containerId;
    private final String networkId;

    private final PiProcessHandle process;
    private final JsonlStdoutPump pump;
    private final JsonlStdinWriter writer;
    private final FrameRingBuffer ring;
    private final CopyOnWriteArrayList<FrameSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final InteractiveSandboxMetrics metrics;
    private final Duration defaultGrace;

    private final AtomicReference<AttachedSandboxState> state = new AtomicReference<>(AttachedSandboxState.ATTACHED);
    private final AtomicReference<EvictionReason> terminalReason = new AtomicReference<>();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private final CompletableFuture<Void> firstFrame = new CompletableFuture<>();

    private volatile Instant lastActivityAt;
    private final Instant attachedAt;

    /** Serialises (a) ring.offer + subscriber fan-out from the pump and (b) snapshot + add from {@link #subscribe}. Guarantees subscribers never see live frames before their snapshot replay. */
    private final Object subscriberLock = new Object();

    private final Consumer<DockerAttachedSandboxAdapter> onClosed;
    private final LifecycleOps lifecycle;
    private final int subscriberQueueCapacity;
    /**
     * Executor used to run {@link #runClose}. Must be a platform-thread pool because the close
     * path invokes docker-java sync APIs whose {@code synchronized} blocks pin virtual threads on
     * JDK 21 (same reason {@code SandboxContainerManager} uses {@code dockerWaitExecutor}).
     */
    private final Executor closeExecutor;

    DockerAttachedSandboxAdapter(
        UUID sessionId,
        String userId,
        String workspaceId,
        String containerId,
        String networkId,
        PiProcessHandle process,
        ObjectMapper mapper,
        FrameRingBuffer ring,
        int subscriberQueueCapacity,
        int stdinWriteTimeoutMs,
        int sendQueueCapacity,
        int maxLineChars,
        Duration defaultGrace,
        InteractiveSandboxMetrics metrics,
        LifecycleOps lifecycle,
        Executor closeExecutor,
        Consumer<DockerAttachedSandboxAdapter> onClosed
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.containerId = containerId;
        this.networkId = networkId;
        this.process = process;
        this.ring = ring;
        this.metrics = metrics;
        this.lifecycle = lifecycle;
        this.onClosed = onClosed;
        this.subscriberQueueCapacity = subscriberQueueCapacity;
        this.defaultGrace = defaultGrace;
        this.closeExecutor = closeExecutor;
        this.attachedAt = Instant.now();
        this.lastActivityAt = this.attachedAt;

        Map<String, String> mdcSnapshot = mdcContext();
        this.writer = new JsonlStdinWriter(
            sessionId,
            process.stdin(),
            mapper,
            sendQueueCapacity,
            stdinWriteTimeoutMs,
            metrics.sendRejectedQueueFull,
            metrics.sendRejectedWriteTimeout,
            metrics.sendRejectedBrokenPipe,
            metrics.sendRejectedClosed,
            metrics.sendBytes,
            () -> terminate(EvictionReason.ERROR),
            mdcSnapshot
        );
        this.pump = new JsonlStdoutPump(
            sessionId,
            process.stdout(),
            mapper,
            this::onFrame,
            this::onEof,
            process::exitValueOrAlive,
            metrics.frameParseError,
            maxLineChars,
            mdcSnapshot
        );
    }

    void start() {
        pump.start();
        writer.start();
    }

    @Override
    public UUID sessionId() {
        return sessionId;
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public String workspaceId() {
        return workspaceId;
    }

    /** Current lifecycle state. Not on the SPI — consumers observe state via thrown {@link #send} exceptions or {@link Disposable#isDisposed()}. */
    public AttachedSandboxState state() {
        return state.get();
    }

    @Override
    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    @Override
    public Duration idleFor() {
        return Duration.between(lastActivityAt, Instant.now());
    }

    @Override
    public void send(JsonNode frame) {
        if (state.get() != AttachedSandboxState.ATTACHED) {
            metrics.sendRejectedClosed.increment();
            throw new InteractiveSandboxException("Session is " + state.get());
        }
        writer.send(frame);
        lastActivityAt = Instant.now();
    }

    @Override
    public Disposable subscribe(Consumer<JsonNode> listener) {
        // Reject during CLOSING too. Letting a subscribe through during CLOSING would spawn a
        // dispatcher virtual thread that no one ever wakes — `runClose` already cleared the
        // subscriptions list, so the late entry isn't disposed by the close path.
        AttachedSandboxState s = state.get();
        if (s != AttachedSandboxState.ATTACHED) {
            FrameSubscription disposed = new FrameSubscription(
                listener,
                1,
                metrics.subscriberDropped,
                metrics.subscriberError,
                () -> {}
            );
            disposed.dispose();
            return disposed;
        }
        FrameSubscription[] holder = new FrameSubscription[1];
        FrameSubscription sub = new FrameSubscription(
            listener,
            subscriberQueueCapacity,
            metrics.subscriberDropped,
            metrics.subscriberError,
            () -> subscriptions.remove(holder[0])
        );
        holder[0] = sub;

        // Under the subscriber lock: take the snapshot AND add to the subscriptions list as one
        // atomic act, in that order. The pump's onFrame also takes this lock before
        // ring.offer + subscriber iteration, so we can never miss a live frame nor receive a
        // live frame before its snapshot replay.
        synchronized (subscriberLock) {
            // Race with concurrent close: state may have transitioned since the outer check.
            if (state.get() != AttachedSandboxState.ATTACHED) {
                sub.dispose();
                return sub;
            }
            List<JsonNode> snapshot = ring.snapshotSince(-1L);
            subscriptions.add(sub);
            sub.start();
            for (JsonNode frame : snapshot) {
                sub.offer(frame);
            }
        }
        return sub;
    }

    @Override
    public void close(Duration graceTimeout) {
        Duration grace = graceTimeout != null ? graceTimeout : defaultGrace;
        if (state.compareAndSet(AttachedSandboxState.ATTACHED, AttachedSandboxState.CLOSING)) {
            terminalReason.compareAndSet(null, EvictionReason.MANUAL);
            runCloseAsync(grace);
        }
        waitForClose(grace.plusSeconds(5));
    }

    @Override
    public boolean writeStalled(long nowNanos) {
        return writer.writeStalled(nowNanos);
    }

    @Override
    public void onWriteTimeout() {
        terminate(EvictionReason.ERROR);
        // Unblock the writer thread by killing the exec subprocess (closes stdin FD on Linux).
        process.destroyForcibly();
    }

    /**
     * Forced close with a specific reason and the registry-supplied default grace. Idempotent.
     * Invoked from: the pump's EOF handler (NATURAL_EXIT / ERROR), the writer on broken-pipe or
     * stdin timeout (ERROR), the reaper on idle (IDLE), the attach-failure cleanup (ERROR).
     */
    void terminate(EvictionReason reason) {
        if (!state.compareAndSet(AttachedSandboxState.ATTACHED, AttachedSandboxState.CLOSING)) {
            return;
        }
        terminalReason.compareAndSet(null, reason);
        if (!firstFrame.isDone()) {
            firstFrame.completeExceptionally(new InteractiveSandboxException("Terminated before first frame"));
        }
        runCloseAsync(defaultGrace);
    }

    /** Block up to {@code timeout} for full close. Used by the registry on shutdown / by attach() teardown. */
    void awaitClosed(Duration timeout) {
        waitForClose(timeout);
    }

    /**
     * Block up to {@code timeout} for the first stdout frame.
     *
     * @return {@code true} if a frame was observed, {@code false} if the timeout elapsed with no
     *     frame
     * @throws InteractiveSandboxException if the pump/writer terminated the session before any
     *     frame arrived (runner crashed, broken pipe, daemon died). Distinguishing this from a
     *     pure timeout lets the adapter charge the right {@code mentor.attach.failure} reason.
     */
    boolean awaitFirstFrame(Duration timeout) {
        try {
            firstFrame.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException te) {
            return false;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof InteractiveSandboxException ise) {
                ise.addSuppressed(ee);
                throw ise;
            }
            throw new InteractiveSandboxException("First frame failed", ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new InteractiveSandboxException("Interrupted while waiting for first frame", ie);
        }
    }

    String containerId() {
        return containerId;
    }

    String networkId() {
        return networkId;
    }

    EvictionReason terminalReason() {
        return terminalReason.get();
    }

    Instant attachedAt() {
        return attachedAt;
    }

    private void onFrame(JsonNode frame, int wireBytes) {
        lastActivityAt = Instant.now();
        if (!firstFrame.isDone()) {
            firstFrame.complete(null);
        }
        metrics.recvBytes.increment(wireBytes);
        synchronized (subscriberLock) {
            ring.offer(frame);
            for (FrameSubscription sub : subscriptions) {
                sub.offer(frame);
            }
        }
    }

    /** Wait this long for the exec subprocess to publish its exit code after EOF on stdout. */
    private static final Duration EOF_EXIT_WAIT = Duration.ofSeconds(2);

    /**
     * Pump-EOF handler. Determines NATURAL_EXIT vs ERROR by waiting briefly for the exec
     * subprocess to publish its exit code — the exec FD can close a few millis (sometimes longer
     * under load) before {@code Process.exitValue()} becomes available. If the exec subprocess is
     * STILL alive after the wait, the daemon is misbehaving — classify as ERROR.
     */
    private void onEof(int initialExitCode) {
        int exit = initialExitCode;
        if (exit < 0) {
            process.waitFor(EOF_EXIT_WAIT);
            exit = process.exitValueOrAlive();
        }
        EvictionReason reason = exit == 0 ? EvictionReason.NATURAL_EXIT : EvictionReason.ERROR;
        terminate(reason);
    }

    private void runCloseAsync(Duration graceTimeout) {
        // Platform-thread executor on purpose: runClose calls docker-java sync APIs that pin
        // virtual carriers on JDK 21 (Apache HttpClient5 synchronized internals). The sync
        // sandbox solved this with `dockerWaitExecutor`; we re-use that bean here.
        try {
            closeExecutor.execute(() -> runClose(graceTimeout));
        } catch (java.util.concurrent.RejectedExecutionException ree) {
            // The executor was shut down between CAS and submit — happens if a @Scheduled tick
            // (reaper / watchdog) fires during Spring's bean destruction. Run inline as fallback;
            // the calling thread is already on shutdown's critical path.
            log.warn("closeExecutor rejected runClose for sessionId={} — running inline", sessionId);
            runClose(graceTimeout);
        }
    }

    private void runClose(Duration graceTimeout) {
        applyMdc();
        try {
            int graceSeconds = (int) Math.max(0, graceTimeout.toSeconds());
            log.info("Closing attached sandbox: reason={}, graceSeconds={}", terminalReason.get(), graceSeconds);

            // 1. Mark the writer terminal first so queued envelopes fail fast and any subsequent
            //    send() throws CLOSED rather than getting through the door to a stdin we're about
            //    to close. close() is idempotent against later destroyForcibly.
            try {
                writer.close();
            } catch (Exception e) {
                log.debug("writer.close() during sandbox close threw: {}", e.getMessage());
            }

            // 2. Ask Docker to stop the container with our grace. SIGTERM → wait → SIGKILL.
            try {
                lifecycle.stopContainer(containerId, graceSeconds);
            } catch (Exception e) {
                log.warn("stopContainer failed during close: {}", e.getMessage());
            }

            // 3. Bounded wait for the exec subprocess to notice and exit. Container stop closes
            //    the docker exec stream; if for any reason it doesn't, destroyForcibly the
            //    subprocess so we never leak a virtual thread waiting forever.
            Duration waitBudget = graceTimeout.plusSeconds(5);
            if (!process.waitFor(waitBudget)) {
                log.warn("Exec subprocess still alive after stop + grace; destroyForcibly");
                process.destroyForcibly();
            }

            // 4. Dispose subscribers — pump's EOF may already have completed; cleanup either way.
            int subscriberCount = subscriptions.size();
            for (FrameSubscription sub : subscriptions) {
                try {
                    sub.dispose();
                } catch (Exception ignored) {}
            }
            subscriptions.clear();

            // 5. Tear down container + network. forceRemove is idempotent against a missing container.
            try {
                lifecycle.removeContainer(containerId);
            } catch (Exception e) {
                log.warn("forceRemove container failed during close: {}", e.getMessage());
            }
            if (networkId != null) {
                try {
                    lifecycle.disconnectAndRemoveNetwork(networkId);
                } catch (Exception e) {
                    log.warn("Network teardown failed during close: {}", e.getMessage());
                }
            }

            // 6. Close FDs with a bounded wait (the docker exec subprocess is already exited or destroyed).
            try {
                process.awaitExitAndClose(Duration.ofSeconds(2));
            } catch (Exception ignored) {}

            // 7. Metrics + state.
            metrics.lifetime.record(Duration.between(attachedAt, Instant.now()));
            metrics.subscribersAtClose.record(subscriberCount);
            EvictionReason reason = terminalReason.get();
            if (reason == null) reason = EvictionReason.ERROR;
            metrics.evictionsBy(reason).increment();

            state.set(AttachedSandboxState.CLOSED);
            try {
                onClosed.accept(this);
            } catch (Exception e) {
                log.warn("Registry onClosed callback threw: {}", e.getMessage());
            }
            closed.complete(null);
            log.info(
                "Sandbox closed: reason={}, lifetimeMs={}",
                reason,
                Duration.between(attachedAt, Instant.now()).toMillis()
            );
        } catch (Throwable t) {
            log.error("Unexpected error during sandbox close", t);
            state.set(AttachedSandboxState.CLOSED);
            closed.completeExceptionally(t);
        } finally {
            MDC.clear();
        }
    }

    private void waitForClose(Duration timeout) {
        try {
            closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Best-effort: caller may have set a tight grace and we exceeded it.
        }
    }

    private Map<String, String> mdcContext() {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        Map<String, String> own = new HashMap<>(ctx != null ? ctx : Map.of());
        own.put(MDC_SESSION_ID, sessionId.toString());
        own.put(MDC_CONTAINER_ID, containerId);
        return Map.copyOf(own);
    }

    private void applyMdc() {
        MDC.put(MDC_SESSION_ID, sessionId.toString());
        MDC.put(MDC_CONTAINER_ID, containerId);
    }

    /** Adapter-provided container / network teardown. */
    interface LifecycleOps {
        void stopContainer(String containerId, int graceSeconds);
        void removeContainer(String containerId);
        void disconnectAndRemoveNetwork(String networkId);
    }
}
