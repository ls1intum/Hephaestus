package de.tum.cit.aet.hephaestus.core.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Marker {@code @Configuration} for the {@code worker} runtime role. Same shape as
 * {@link ServerRoleConfiguration}: gates worker-only subsystems (Docker sandbox runtime,
 * agent NATS pull consumer, reconcilers, zombie sweepers) via a single composition-layer
 * conditional.
 *
 * <p>{@code matchIfMissing = true} so the JAR boots full-monolith with zero env vars.
 * Worker-only deploys set {@code hephaestus.runtime.worker.enabled=true} and
 * {@code hephaestus.runtime.server.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = RuntimeRole.WORKER_PROPERTY,
    havingValue = "true",
    matchIfMissing = true
)
public class WorkerRoleConfiguration {
    // Marker. Bean factories can be added here as worker-only components are migrated.
}
