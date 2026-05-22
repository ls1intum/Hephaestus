package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JavaJwtWorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtHandshakeInterceptor;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerKeyRing;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenDenylist;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenDenylistRepository;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenProperties;
import de.tum.cit.aet.hephaestus.core.runtime.hub.session.HubSessionInbox;
import de.tum.cit.aet.hephaestus.core.runtime.hub.session.HubSessionRegistry;
import de.tum.cit.aet.hephaestus.core.runtime.hub.session.MentorSessionBridge;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the worker hub: WSS endpoint registration, JWT auth, session registry. Gated by
 * {@link RuntimeRole#SERVER_PROPERTY} with {@code matchIfMissing=true} — the application-server
 * runtime role hosts the hub; webhook and worker pods do not.
 *
 * <p>The {@code WebSocketConfigurer} lives in a separate {@link HubWebSocketRegistration} class
 * so it can depend on this configuration's beans without a constructor cycle.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ HubProperties.class, WorkerTokenProperties.class })
@EnableWebSocket
public class HubConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(FrameCodec.class)
    FrameCodec frameCodec(ObjectMapper objectMapper) {
        return new FrameCodec(objectMapper);
    }

    @Bean
    WorkerSessionRegistry workerSessionRegistry(ApplicationEventPublisher events, MeterRegistry meterRegistry) {
        return new WorkerSessionRegistry(events, meterRegistry);
    }

    @Bean
    WorkerKeyRing workerKeyRing(WorkerTokenProperties properties) {
        return WorkerKeyRing.fromConfig(properties);
    }

    @Bean
    WorkerTokenDenylist workerTokenDenylist(WorkerTokenDenylistRepository repository) {
        return new WorkerTokenDenylist(repository);
    }

    @Bean
    WorkerJwtVerifier workerJwtVerifier(
        WorkerKeyRing keyRing,
        WorkerTokenProperties properties,
        WorkerTokenDenylist denylist
    ) {
        return new JavaJwtWorkerJwtVerifier(keyRing, properties, denylist);
    }

    @Bean
    WorkerJwtIssuer workerJwtIssuer(WorkerKeyRing keyRing, WorkerTokenProperties properties) {
        return new WorkerJwtIssuer(keyRing, properties);
    }

    @Bean
    WorkerJwtHandshakeInterceptor workerJwtHandshakeInterceptor(WorkerJwtVerifier verifier) {
        return new WorkerJwtHandshakeInterceptor(verifier);
    }

    @Bean
    WorkerControlWebSocketHandler workerControlWebSocketHandler(
        WorkerSessionRegistry registry,
        FrameCodec codec,
        HubProperties properties,
        Optional<HubSessionInbox> sessionInbox,
        MeterRegistry meterRegistry
    ) {
        return new WorkerControlWebSocketHandler(registry, codec, properties, sessionInbox, meterRegistry);
    }

    @Bean
    HubWebSocketRegistration hubWebSocketRegistration(
        HubProperties properties,
        WorkerControlWebSocketHandler handler,
        WorkerJwtHandshakeInterceptor interceptor
    ) {
        return new HubWebSocketRegistration(properties, handler, interceptor);
    }

    @Bean
    HubSessionRegistry hubSessionRegistry() {
        return new HubSessionRegistry();
    }

    /**
     * The bridge implements {@link HubSessionInbox} directly — Spring picks it up under both
     * the concrete type (consumed by controllers that need {@code open()}/{@code sendInput()})
     * and the interface (consumed by {@link WorkerControlWebSocketHandler} for forwarding
     * worker-originated SessionOutput/Close frames). No second alias bean needed.
     */
    @Bean
    MentorSessionBridge mentorSessionBridge(
        WorkerSessionRegistry workerRegistry,
        HubSessionRegistry sessionRegistry,
        MeterRegistry meterRegistry
    ) {
        return new MentorSessionBridge(workerRegistry, sessionRegistry, meterRegistry);
    }
}
