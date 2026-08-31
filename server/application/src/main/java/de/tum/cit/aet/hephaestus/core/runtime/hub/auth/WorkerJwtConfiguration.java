package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${" + RuntimeRole.SERVER_PROPERTY + ":true} or ${" + RuntimeRole.WORKER_PROPERTY + ":true}")
@EnableConfigurationProperties(WorkerTokenProperties.class)
public class WorkerJwtConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkerJwtConfiguration.class);

    @Bean
    WorkerKeyRing workerKeyRing(WorkerTokenProperties properties, Environment environment) {
        WorkerKeyRing ring = WorkerKeyRing.fromConfig(properties);
        if (ring.active().ephemeral()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                log.warn(
                        "Worker JWT is using an EPHEMERAL signing key (kid={}). Configure a stable key via "
                                + "hephaestus.worker.hub.token.keys[*].private-key for production.",
                        ring.active().kid());
            } else {
                log.info(
                        "Worker JWT using ephemeral signing key (kid={}) — fine for dev.",
                        ring.active().kid());
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
            MeterRegistry meterRegistry) {
        return new JavaJwtWorkerJwtVerifier(keyRing, properties, denylist, meterRegistry);
    }

    @Bean
    WorkerJwtIssuer workerJwtIssuer(WorkerKeyRing keyRing, WorkerTokenProperties properties) {
        return new WorkerJwtIssuer(keyRing, properties);
    }
}
