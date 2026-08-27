package de.tum.cit.aet.hephaestus.core.runtime.hub;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JavaJwtWorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtHandshakeInterceptor;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerKeyRing;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenDenylistRepository;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenDenylistService;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerTokenProperties;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the worker hub: WSS endpoint registration, JWT auth, session registry. Gated by
 * {@link RuntimeRole#SERVER_PROPERTY} with {@code matchIfMissing=true} — the application-server
 * runtime role hosts the hub; webhook and worker pods do not.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ WorkerTokenProperties.class })
@EnableWebSocket
public class HubConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HubConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(FrameCodec.class)
    FrameCodec frameCodec(ObjectMapper objectMapper) {
        return new FrameCodec(objectMapper);
    }

    @Bean
    WorkerSessionRegistry workerSessionRegistry(ApplicationEventPublisher events, MeterRegistry meterRegistry) {
        return new WorkerSessionRegistry(events, meterRegistry);
    }

    @Bean
    WorkerJobCancelDispatcher workerJobCancelDispatcher(WorkerSessionRegistry registry) {
        return new WorkerJobCancelDispatcher(registry);
    }

    @Bean
    WorkerKeyRing workerKeyRing(WorkerTokenProperties properties, Environment environment) {
        WorkerKeyRing ring = WorkerKeyRing.fromConfig(properties);
        if (ring.active().ephemeral()) {
            // Ephemeral keys are fine for dev but unsafe in prod (worker reconnects across pod
            // restarts must verify already-issued JWTs), so only the prod profile warns.
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                log.warn(
                    "Worker JWT is using an EPHEMERAL signing key (kid={}). Configure a stable key via " +
                        "hephaestus.worker.hub.token.keys[*].private-key for production.",
                    ring.active().kid()
                );
            } else {
                log.info("Worker JWT using ephemeral signing key (kid={}) — fine for dev.", ring.active().kid());
            }
        }
        return ring;
    }

    @Bean
    WorkerTokenDenylistService workerTokenDenylistService(WorkerTokenDenylistRepository repository) {
        return new WorkerTokenDenylistService(repository);
    }

    @Bean
    WorkerJwtVerifier workerJwtVerifier(
        WorkerKeyRing keyRing,
        WorkerTokenProperties properties,
        WorkerTokenDenylistService denylist,
        MeterRegistry meterRegistry
    ) {
        return new JavaJwtWorkerJwtVerifier(keyRing, properties, denylist, meterRegistry);
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
        MeterRegistry meterRegistry
    ) {
        return new WorkerControlWebSocketHandler(registry, codec, meterRegistry);
    }

    @Bean
    HubWebSocketRegistration hubWebSocketRegistration(
        WorkerControlWebSocketHandler handler,
        WorkerJwtHandshakeInterceptor interceptor
    ) {
        return new HubWebSocketRegistration(handler, interceptor);
    }
}
