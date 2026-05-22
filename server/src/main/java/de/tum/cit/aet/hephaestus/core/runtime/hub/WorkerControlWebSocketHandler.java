package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwt;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtHandshakeInterceptor;
import de.tum.cit.aet.hephaestus.core.runtime.hub.session.HubSessionInbox;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.ForceReconnect;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameEnvelope;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.Heartbeat;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionInput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOpen;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOutput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerHello;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerWelcome;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class WorkerControlWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkerControlWebSocketHandler.class);
    static final String ATTR_WORKER_SESSION = "worker.session";

    private final WorkerSessionRegistry registry;
    private final FrameCodec codec;
    private final HubProperties hubProperties;
    private final Optional<HubSessionInbox> sessionInbox;
    private final MeterRegistry meterRegistry;

    public WorkerControlWebSocketHandler(
        WorkerSessionRegistry registry,
        FrameCodec codec,
        HubProperties hubProperties,
        Optional<HubSessionInbox> sessionInbox,
        MeterRegistry meterRegistry
    ) {
        this.registry = registry;
        this.codec = codec;
        this.hubProperties = hubProperties;
        this.sessionInbox = sessionInbox;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawTransport) {
        WorkerJwt jwt = (WorkerJwt) rawTransport.getAttributes().get(WorkerJwtHandshakeInterceptor.ATTR_JWT);
        if (jwt == null) {
            log.warn("WSS upgrade arrived without verified JWT attribute — closing.");
            close(rawTransport, CloseStatus.POLICY_VIOLATION);
            return;
        }
        rawTransport.setTextMessageSizeLimit(hubProperties.maxFrameSizeBytes());
        rawTransport.setBinaryMessageSizeLimit(hubProperties.maxFrameSizeBytes());
        // Wrap with a concurrent decorator: caps the per-session outbound buffer and a send
        // deadline, so a slow worker cannot balloon hub memory or hold a sender thread.
        WebSocketSession transport = new ConcurrentWebSocketSessionDecorator(
            rawTransport,
            (int) hubProperties.sendTimeLimit().toMillis(),
            hubProperties.sendBufferSizeBytes()
        );
        String sessionId = UUID.randomUUID().toString();
        WorkerSession session = new WorkerSession(jwt.workerId(), sessionId, jwt.jti(), jwt.expiresAt(), transport, codec);
        rawTransport.getAttributes().put(ATTR_WORKER_SESSION, session);
        log.info("WSS connection opened: workerId={}, sessionId={}, jwtExpiresAt={}", jwt.workerId(), sessionId, jwt.expiresAt());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession transport, BinaryMessage message) {
        WorkerSession session = (WorkerSession) transport.getAttributes().get(ATTR_WORKER_SESSION);
        String workerId = session != null ? session.workerId() : "<unauthenticated>";
        log.warn("Refusing binary frame from workerId={}: text-only protocol", workerId);
        meterRegistry.counter("worker.hub.binary.refused").increment();
        close(transport, CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    protected void handleTextMessage(WebSocketSession transport, TextMessage message) {
        WorkerSession session = (WorkerSession) transport.getAttributes().get(ATTR_WORKER_SESSION);
        if (session == null) {
            close(transport, CloseStatus.SERVER_ERROR);
            return;
        }
        FrameEnvelope envelope;
        try {
            envelope = codec.decode(message.getPayload());
        } catch (RuntimeException e) {
            log.warn("Frame decode failed for workerId={}: {}", session.workerId(), e.getClass().getSimpleName());
            meterRegistry.counter("worker.hub.frame.decode.failed").increment();
            close(transport, CloseStatus.BAD_DATA);
            return;
        }
        session.markInbound();
        WorkerControlFrame frame = envelope.payload();
        try {
            dispatch(session, frame);
            maybeForceReconnect(session);
            return;
        } catch (RuntimeException e) {
            log.error("Hub frame dispatch threw for workerId={}, frame={}", session.workerId(), frame.getClass().getSimpleName(), e);
            meterRegistry.counter("worker.hub.frame.dispatch.failed").increment();
        }
    }

    private void dispatch(WorkerSession session, WorkerControlFrame frame) {
        switch (frame) {
            case WorkerHello hello -> handleHello(session, hello);
            case CapacityReport capacity -> session.updateCapacity(capacity);
            case Heartbeat heartbeat -> {
                if (heartbeat.draining()) {
                    log.info("Worker {} signalled draining", session.workerId());
                    meterRegistry.counter("worker.hub.draining.signalled").increment();
                }
            }
            case SessionOutput output -> sessionInbox.ifPresent(inbox -> inbox.onSessionOutput(output));
            case SessionClose close -> sessionInbox.ifPresent(inbox -> inbox.onSessionClose(close));
            case WorkerWelcome w -> warnUnexpectedFrame(session, w);
            case ForceReconnect f -> warnUnexpectedFrame(session, f);
            case SessionOpen o -> warnUnexpectedFrame(session, o);
            case SessionInput i -> warnUnexpectedFrame(session, i);
        }
    }

    private void warnUnexpectedFrame(WorkerSession session, WorkerControlFrame frame) {
        log.warn("Unexpected hub-bound frame {} from workerId={}",
            frame.getClass().getSimpleName(), session.workerId());
    }

    private void handleHello(WorkerSession session, WorkerHello hello) {
        if (!hello.workerId().equals(session.workerId())) {
            log.warn(
                "WorkerHello workerId={} does not match JWT subject {}; closing",
                hello.workerId(),
                session.workerId()
            );
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        List<Integer> supported = hello.supportedVersions();
        if (!supported.contains(FrameEnvelope.CURRENT_VERSION)) {
            log.warn(
                "WorkerHello from {} does not support protocol v{}; closing",
                session.workerId(),
                FrameEnvelope.CURRENT_VERSION
            );
            session.close(new CloseStatus(4400, "unsupported protocol version"));
            return;
        }
        registry.register(session);
        WorkerWelcome welcome = new WorkerWelcome(FrameEnvelope.CURRENT_VERSION, session.sessionId());
        session.send(welcome);
        meterRegistry.counter("worker.hub.handshake.completed").increment();
    }

    private void maybeForceReconnect(WorkerSession session) {
        Duration threshold = hubProperties.forceReconnectThreshold();
        if (threshold.isZero()) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), session.jwtExpiresAt());
        if (!remaining.isNegative() && remaining.compareTo(threshold) <= 0 && session.markForceReconnectSent()) {
            session.send(new ForceReconnect("jwt near expiry"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession transport, CloseStatus status) {
        WorkerSession session = (WorkerSession) transport.getAttributes().remove(ATTR_WORKER_SESSION);
        if (session != null) {
            registry.unregister(session, "ws-close:" + status.getCode());
            log.info("WSS connection closed: workerId={}, sessionId={}, status={}", session.workerId(), session.sessionId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession transport, Throwable exception) {
        WorkerSession session = (WorkerSession) transport.getAttributes().get(ATTR_WORKER_SESSION);
        String workerId = session != null ? session.workerId() : "<unauthenticated>";
        log.warn("WSS transport error for workerId={}: {}", workerId, exception.getClass().getSimpleName());
        meterRegistry.counter("worker.hub.transport.errors").increment();
    }

    private static void close(WebSocketSession transport, CloseStatus status) {
        try {
            transport.close(status);
        } catch (IOException ignored) {
            // close-during-close is fine
        }
    }
}
