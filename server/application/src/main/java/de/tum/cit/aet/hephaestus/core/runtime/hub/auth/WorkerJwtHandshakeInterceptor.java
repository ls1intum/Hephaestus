package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Authenticates worker control-channel WebSocket upgrades. */
public class WorkerJwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WorkerJwtHandshakeInterceptor.class);
    public static final String ATTR_JWT = "worker.jwt";

    private final WorkerJwtVerifier verifier;

    public WorkerJwtHandshakeInterceptor(WorkerJwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = extractBearer(request.getHeaders().get(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            log.debug("worker handshake rejected: missing or unsupported Authorization header");
            reject401(response);
            return false;
        }
        try {
            WorkerJwt jwt = verifier.verify(token);
            if (!(jwt instanceof WorkerSessionJwt)) {
                log.warn("worker handshake rejected: token is not scoped to a worker session");
                reject401(response);
                return false;
            }
            attributes.put(ATTR_JWT, jwt);
            return true;
        } catch (WorkerJwtInvalidException e) {
            log.warn("worker handshake rejected: {}", e.getMessage());
            reject401(response);
            return false;
        }
    }

    private static void reject401(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("WWW-Authenticate", "Bearer realm=\"worker-hub\"");
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            @Nullable Exception exception) {}

    private static @Nullable String extractBearer(@Nullable List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (String header : headers) {
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = header.substring(7).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
        }
        return null;
    }
}
