package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtHandshakeInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

public class HubWebSocketRegistration implements WebSocketConfigurer {

    private final HubProperties hubProperties;
    private final WorkerControlWebSocketHandler handler;
    private final WorkerJwtHandshakeInterceptor interceptor;

    public HubWebSocketRegistration(
        HubProperties hubProperties,
        WorkerControlWebSocketHandler handler,
        WorkerJwtHandshakeInterceptor interceptor
    ) {
        this.hubProperties = hubProperties;
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // No allowed origins: non-browser workers don't send Origin and pass through; browsers
        // can't construct a Bearer-authenticated upgrade anyway, so a wildcard would only invite
        // CSWSH against a future cookie-auth path. RFC 6455 §10.2.
        registry
            .addHandler(handler, hubProperties.path())
            .addInterceptors(interceptor);
    }
}
