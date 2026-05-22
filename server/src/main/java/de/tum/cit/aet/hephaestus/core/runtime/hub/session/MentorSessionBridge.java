package de.tum.cit.aet.hephaestus.core.runtime.hub.session;

import de.tum.cit.aet.hephaestus.core.runtime.hub.WorkerSession;
import de.tum.cit.aet.hephaestus.core.runtime.hub.WorkerSessionRegistry;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionInput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionKind;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOpen;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOutput;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

/**
 * SSE ↔ WSS bridge for browser-facing mentor sessions. First-fit worker selection (least-loaded
 * is dispatcher-owned).
 *
 * <p>Implements {@link HubSessionInbox} so the WSS handler can forward worker-originated
 * {@link SessionOutput} / {@link SessionClose} frames in without knowing about the SSE layer.
 *
 * <p>Race condition (intentional MVP): two near-simultaneous opens may pick the same worker
 * with {@code spareMentor=1}; the second open arrives as {@code SessionOpen} on the worker
 * which rejects with {@code SessionClose{ERROR}}. The bridge surfaces that as a completed
 * emitter; the caller (HTTP layer) sees the empty SSE stream and can retry. CAS / soft
 * reservation is the dispatcher's job.
 */
public class MentorSessionBridge implements HubSessionInbox {

    private static final Logger log = LoggerFactory.getLogger(MentorSessionBridge.class);

    /** Browser SSE emitters expire after this; mentor turns rarely exceed 10m. */
    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final WorkerSessionRegistry workerRegistry;
    private final HubSessionRegistry sessionRegistry;
    private final Counter sessionsOpened;
    private final Counter noCapacity;

    public MentorSessionBridge(
        WorkerSessionRegistry workerRegistry,
        HubSessionRegistry sessionRegistry,
        MeterRegistry meterRegistry
    ) {
        this.workerRegistry = workerRegistry;
        this.sessionRegistry = sessionRegistry;
        this.sessionsOpened = Counter.builder("worker.hub.mentor.opened")
            .description("Bridged mentor sessions opened against a chosen worker")
            .register(meterRegistry);
        this.noCapacity = Counter.builder("worker.hub.mentor.rejected")
            .description("Bridged mentor opens rejected because no worker had spare capacity")
            .register(meterRegistry);
    }

    /**
     * Open a new mentor session. The returned {@link SseEmitter} streams {@link SessionOutput}
     * payloads as SSE events; a terminal output (or {@link SessionClose}) completes it.
     *
     * @return open emitter on success; an empty {@link Optional} when no worker has spare
     *     mentor capacity (caller maps to HTTP 503).
     */
    public Optional<BridgeOpen> open(JsonNode context) {
        Optional<WorkerSession> chosen = pickWorker();
        if (chosen.isEmpty()) {
            noCapacity.increment();
            return Optional.empty();
        }
        WorkerSession worker = chosen.get();
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        HubSessionRegistry.BridgeState state = new HubSessionRegistry.BridgeState(worker, emitter, Instant.now());
        sessionRegistry.put(sessionId, state);

        // Wire emitter lifecycle so a browser-side disconnect closes the worker session.
        emitter.onCompletion(() -> finalize(sessionId, "completion"));
        emitter.onTimeout(() -> finalize(sessionId, "timeout"));
        emitter.onError(t -> finalize(sessionId, "error:" + t.getClass().getSimpleName()));

        boolean sent = worker.send(new SessionOpen(sessionId, SessionKind.MENTOR_INTERACTIVE, context));
        if (!sent) {
            sessionRegistry.remove(sessionId);
            emitter.completeWithError(new IOException("worker send failed"));
            return Optional.empty();
        }
        sessionsOpened.increment();
        log.info("Bridge opened mentor session {} → worker {}", sessionId, worker.workerId());
        return Optional.of(new BridgeOpen(sessionId, emitter));
    }

    /**
     * Forward a {@link SessionInput} from the browser to the worker. Returns {@code true} when
     * the input was queued for delivery; {@code false} if the session is unknown or the worker
     * is no longer reachable (caller maps to HTTP 410 GONE).
     */
    public boolean sendInput(String sessionId, String payload) {
        return sessionRegistry
            .get(sessionId)
            .map(state -> state.worker().send(new SessionInput(sessionId, payload)))
            .orElse(false);
    }

    /** Explicitly close a session from the browser side. Idempotent. */
    public void close(String sessionId, SessionCloseReason reason) {
        sessionRegistry
            .remove(sessionId)
            .ifPresent(state -> {
                state.worker().send(new SessionClose(sessionId, reason));
                completeEmitter(state.emitter(), null);
            });
    }

    // ── HubSessionInbox ──

    @Override
    public void onSessionOutput(SessionOutput out) {
        sessionRegistry
            .get(out.sessionId())
            .ifPresent(state -> {
                try {
                    state.emitter().send(SseEmitter.event().data(out.payload()));
                } catch (IOException e) {
                    log.debug("SSE send failed for session {}: {}", out.sessionId(), e.getClass().getSimpleName());
                    finalize(out.sessionId(), "sse-send-failed");
                }
                if (out.terminal()) {
                    sessionRegistry.remove(out.sessionId());
                    completeEmitter(state.emitter(), null);
                }
            });
    }

    @Override
    public void onSessionClose(SessionClose close) {
        sessionRegistry
            .remove(close.sessionId())
            .ifPresent(state -> {
                Throwable error =
                    close.reason() == SessionCloseReason.ERROR ? new IOException("worker session error") : null;
                completeEmitter(state.emitter(), error);
            });
    }

    private Optional<WorkerSession> pickWorker() {
        return workerRegistry
            .sessions()
            .stream()
            .filter(WorkerSession::isOpen)
            .filter(s -> {
                CapacityReport cap = s.lastCapacity();
                return cap != null && cap.spareMentor() > 0;
            })
            .findFirst();
    }

    private void finalize(String sessionId, String reason) {
        sessionRegistry
            .remove(sessionId)
            .ifPresent(state -> {
                state.worker().send(new SessionClose(sessionId, SessionCloseReason.USER_DISCONNECTED));
                log.debug("Bridge session {} finalized ({})", sessionId, reason);
            });
    }

    private static void completeEmitter(SseEmitter emitter, Throwable error) {
        try {
            if (error != null) {
                emitter.completeWithError(error);
            } else {
                emitter.complete();
            }
        } catch (RuntimeException ignored) {
            // Spring may have already completed it on the timeout path; safe to ignore.
        }
    }

    /** Open-session handle returned to controllers. */
    public record BridgeOpen(String sessionId, SseEmitter emitter) {}
}
