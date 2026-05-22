package de.tum.cit.aet.hephaestus.core.runtime.hub.session;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

/**
 * Bridged mentor SSE → WSS-connected worker. Browser POSTs an {@code OpenSessionRequest}; the
 * response is an SSE stream of {@code SessionOutput} payloads. Subsequent input chunks go to
 * {@code POST {sessionId}/input}; explicit close uses {@code DELETE {sessionId}}.
 *
 * <p><strong>Default-off in this PR.</strong> The endpoint forwards a user-supplied
 * {@code context} verbatim to the worker, which uses it to choose the sandbox image / command /
 * env. Until the hub builds a server-side context from a verified workspace membership + image
 * allowlist, this surface is privilege-escalation territory and must be explicitly opted in via
 * {@code hephaestus.worker.hub.bridge.enabled=true}.
 */
@RestController
@RequestMapping("/api/mentor/bridge")
@Tag(name = "Mentor Bridge", description = "WSS-bridged mentor chat (opt-in)")
@ConditionalOnBean(MentorSessionBridge.class)
@Hidden
@de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic(
    "Bridge session is infra-level; workspace context is forwarded in the body and validated by the worker"
)
public class BridgedMentorController {

    private static final Logger log = LoggerFactory.getLogger(BridgedMentorController.class);

    private final MentorSessionBridge bridge;

    public BridgedMentorController(MentorSessionBridge bridge) {
        this.bridge = bridge;
    }

    @PostMapping(value = "/sessions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Open a bridged mentor session; stream output as SSE events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SseEmitter> open(@RequestBody OpenSessionRequest request) {
        if (request == null || request.context() == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<MentorSessionBridge.BridgeOpen> result = bridge.open(request.context());
        if (result.isEmpty()) {
            log.info("Bridged mentor open rejected: no worker has spare mentor capacity");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        MentorSessionBridge.BridgeOpen open = result.get();
        return ResponseEntity.ok().header("X-Mentor-Session-Id", open.sessionId()).body(open.emitter());
    }

    @PostMapping(value = "/sessions/{sessionId}/input", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send an input chunk to a bridged mentor session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> input(@PathVariable String sessionId, @RequestBody InputRequest request) {
        if (request == null || request.payload() == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean forwarded = bridge.sendInput(sessionId, request.payload());
        return forwarded ? ResponseEntity.accepted().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Close a bridged mentor session (USER_DISCONNECTED reason)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> close(@PathVariable String sessionId) {
        bridge.close(sessionId, SessionCloseReason.USER_DISCONNECTED);
        return ResponseEntity.noContent().build();
    }

    /**
     * Browser → hub session-open payload. {@code context} is forwarded verbatim to the worker
     * inside {@code SessionOpen.context}; the worker parses it as
     * {@link de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.MentorSessionContext}.
     */
    public record OpenSessionRequest(JsonNode context) {}

    /** Browser → hub stdin chunk for an open session. {@code payload} is opaque to the bridge. */
    public record InputRequest(String payload) {}
}
