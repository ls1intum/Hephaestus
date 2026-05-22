package de.tum.cit.aet.hephaestus.core.runtime.hub.session;

import de.tum.cit.aet.hephaestus.core.runtime.hub.WorkerSession;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Per-session map: {@code sessionId} → ({@link WorkerSession} that runs it, browser-facing {@link SseEmitter}). */
public class HubSessionRegistry {

    private final Map<String, BridgeState> sessions = new ConcurrentHashMap<>();

    public void put(String sessionId, BridgeState state) {
        sessions.put(sessionId, state);
    }

    public Optional<BridgeState> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<BridgeState> remove(String sessionId) {
        return Optional.ofNullable(sessions.remove(sessionId));
    }

    public int activeCount() {
        return sessions.size();
    }

    public record BridgeState(WorkerSession worker, SseEmitter emitter, Instant openedAt) {}
}
