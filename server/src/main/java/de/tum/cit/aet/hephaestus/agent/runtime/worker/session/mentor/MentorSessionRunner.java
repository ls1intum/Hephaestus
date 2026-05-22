package de.tum.cit.aet.hephaestus.agent.runtime.worker.session.mentor;

import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerControlPublisher;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.MentorSessionContext;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionInput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOpen;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOutput;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Worker-side per-session lifecycle. When sandbox support is present, each {@link SessionOpen}
 * attaches a container via {@link InteractiveSandboxService} and pumps its JSONL stdout back as
 * {@link SessionOutput} frames. Without sandbox support the runner only tracks capacity and
 * echoes an open-ack — useful for cold-start smoke tests and the bridge-only deploy mode.
 */
public class MentorSessionRunner {

    private static final Logger log = LoggerFactory.getLogger(MentorSessionRunner.class);
    private static final Duration CLOSE_GRACE = Duration.ofSeconds(10);

    private final WorkerControlPublisher publisher;
    private final WorkerCapacityState capacityState;
    private final Optional<InteractiveSandboxService> sandboxService;
    private final ObjectMapper objectMapper;
    private final Counter opened;
    private final Counter rejected;
    private final Counter closed;
    private final Counter failed;

    private final Map<String, RunningSession> sessions = new ConcurrentHashMap<>();

    public MentorSessionRunner(
        WorkerControlPublisher publisher,
        WorkerCapacityState capacityState,
        Optional<InteractiveSandboxService> sandboxService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.publisher = publisher;
        this.capacityState = capacityState;
        this.sandboxService = sandboxService;
        this.objectMapper = objectMapper;
        this.opened = Counter.builder("worker.mentor.session.opened")
            .description("Mentor sessions opened on this worker")
            .register(meterRegistry);
        this.rejected = Counter.builder("worker.mentor.session.rejected")
            .description("Mentor session opens rejected (no capacity or bad context)")
            .register(meterRegistry);
        this.closed = Counter.builder("worker.mentor.session.closed")
            .description("Mentor sessions closed (any reason)")
            .register(meterRegistry);
        this.failed = Counter.builder("worker.mentor.session.failed")
            .description("Mentor sessions that ended via SessionClose{ERROR}")
            .register(meterRegistry);
    }

    public void onOpen(SessionOpen open) {
        if (!capacityState.tryClaimMentor()) {
            rejected.increment();
            publisher.send(new SessionClose(open.sessionId(), SessionCloseReason.ERROR));
            return;
        }
        RunningSession session = new RunningSession(open.sessionId());
        if (sessions.putIfAbsent(open.sessionId(), session) != null) {
            capacityState.releaseMentor();
            return;
        }
        opened.increment();

        sandboxService.ifPresentOrElse(
            svc -> attachSandbox(svc, session, open),
            // Sandbox-less mode (cold-start smoke / bridge-only deploys): the WSS plumbing is
            // exercised but no real mentor turn runs. Emit an open-ack so the hub can chart it.
            () -> publisher.send(new SessionOutput(open.sessionId(), "", false))
        );
    }

    public void onInput(SessionInput input) {
        RunningSession session = sessions.get(input.sessionId());
        if (session == null) {
            return;
        }
        AttachedSandbox sandbox = session.sandbox;
        if (sandbox == null) {
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(input.payload());
        } catch (RuntimeException e) {
            log.warn("Mentor session {} received non-JSON input; dropping", input.sessionId());
            return;
        }
        // Serialise per-session: SessionInput frames for different sessions interleave freely.
        synchronized (session.inputLock) {
            try {
                sandbox.send(payload);
            } catch (InteractiveSandboxException e) {
                log.warn("Mentor session {} sandbox.send failed: {}", input.sessionId(), e.getMessage());
                sessions.remove(session.sessionId);
                teardown(session, SessionCloseReason.ERROR);
            }
        }
    }

    public void onClose(SessionClose close) {
        RunningSession session = sessions.remove(close.sessionId());
        if (session == null) {
            return;
        }
        teardown(session, close.reason());
    }

    /** Drain entry point: end every in-flight session with {@code reason}. */
    public void closeAll(SessionCloseReason reason) {
        if (sessions.isEmpty()) {
            return;
        }
        log.info("Closing {} mentor session(s) for drain (reason={})", sessions.size(), reason);
        for (var entry : Map.copyOf(sessions).entrySet()) {
            RunningSession session = sessions.remove(entry.getKey());
            if (session != null) {
                teardown(session, reason);
            }
        }
    }

    public int activeCount() {
        return sessions.size();
    }

    // ── internals ──

    private void attachSandbox(InteractiveSandboxService svc, RunningSession session, SessionOpen open) {
        MentorSessionContext context;
        try {
            context = objectMapper.treeToValue(open.context(), MentorSessionContext.class);
        } catch (RuntimeException e) {
            log.warn("Mentor session {} has invalid context: {}", session.sessionId, e.getMessage());
            rejectOpen(session);
            return;
        }
        InteractiveSandboxSpec spec = buildSpec(open.sessionId(), context);
        AttachedSandbox sandbox;
        try {
            sandbox = svc.attach(spec);
        } catch (InteractiveSandboxException e) {
            log.warn("Mentor session {} sandbox attach failed: {}", session.sessionId, e.getMessage());
            rejectOpen(session);
            return;
        }
        // Assign the sandbox before subscribing: a synchronous emission must see session.sandbox.
        session.sandbox = sandbox;
        Disposable subscription = sandbox.subscribeFromNow(frame -> publishOutput(session.sessionId, frame));
        session.subscription = subscription;
    }

    /**
     * Map a {@link MentorSessionContext} into the sandbox SPI. The hub carries the per-turn fields
     * (user, workspace, image, command, env, optional limit overrides); the worker fills security
     * profile + network policy from its local defaults — the hub is not trusted to override them.
     */
    private InteractiveSandboxSpec buildSpec(String sessionId, MentorSessionContext context) {
        ResourceLimits limits = mergeLimits(context.limits());
        return new InteractiveSandboxSpec(
            UUID.fromString(sessionId),
            context.userId(),
            context.workspaceId(),
            context.image(),
            context.command(),
            context.environment(),
            new NetworkPolicy(false, null, null, null),
            limits,
            SecurityProfile.DEFAULT,
            Map.of(),
            Map.of()
        );
    }

    private static ResourceLimits mergeLimits(MentorSessionContext.Limits overrides) {
        if (overrides == null) {
            return ResourceLimits.DEFAULT;
        }
        return new ResourceLimits(
            overrides.memoryBytes() != null ? overrides.memoryBytes() : ResourceLimits.DEFAULT.memoryBytes(),
            overrides.cpus() != null ? overrides.cpus() : ResourceLimits.DEFAULT.cpus(),
            overrides.pidsLimit() != null ? overrides.pidsLimit() : ResourceLimits.DEFAULT.pidsLimit(),
            ResourceLimits.DEFAULT.maxRuntime()
        );
    }

    private void publishOutput(String sessionId, JsonNode frame) {
        try {
            publisher.send(new SessionOutput(sessionId, objectMapper.writeValueAsString(frame), false));
        } catch (RuntimeException e) {
            log.warn("Mentor session {} output publish failed: {}", sessionId, e.getMessage());
        }
    }

    /** Open path failed before sandbox/subscription assignment — nothing to tear down. */
    private void rejectOpen(RunningSession session) {
        sessions.remove(session.sessionId);
        capacityState.releaseMentor();
        rejected.increment();
        publisher.send(new SessionClose(session.sessionId, SessionCloseReason.ERROR));
    }

    private void teardown(RunningSession session, SessionCloseReason reason) {
        if (session.subscription != null) {
            session.subscription.dispose();
        }
        if (session.sandbox != null) {
            try {
                session.sandbox.close(CLOSE_GRACE);
            } catch (RuntimeException e) {
                log.warn("Mentor session {} sandbox close failed: {}", session.sessionId, e.getMessage());
            }
        }
        capacityState.releaseMentor();
        if (reason == SessionCloseReason.ERROR) {
            failed.increment();
        } else {
            closed.increment();
        }
        publisher.send(new SessionOutput(session.sessionId, "", true));
        publisher.send(new SessionClose(session.sessionId, reason));
    }

    private static final class RunningSession {

        final String sessionId;
        final Object inputLock = new Object();
        volatile AttachedSandbox sandbox;
        volatile Disposable subscription;

        RunningSession(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
