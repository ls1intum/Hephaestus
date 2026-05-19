package de.tum.cit.aet.hephaestus.core.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Marker {@code @Configuration} for the {@code server} runtime role. Gates server-only
 * components via a single composition-layer conditional rather than scattering
 * {@code @ConditionalOnProperty} on every controller, listener, and scheduled task.
 *
 * <p>{@code matchIfMissing = true}: a fresh JAR boots the full monolith with zero env
 * vars — opt-OUT, not opt-IN. The production deploy flips this to {@code false} on
 * worker-only pods. {@code RuntimeRoleBoundaryTest} enforces that no other role gate
 * forgets the {@code matchIfMissing} clause.
 *
 * <p>This marker is intentionally beanless: it exists so future commits can either
 * (a) attach role-gated bean factories here, or (b) consult {@code RuntimeRole.SERVER_PROPERTY}
 * directly on individual beans. The choice is per-bean and depends on bean lifecycle.
 *
 * @see RuntimeRole
 * @see WorkerRoleConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = RuntimeRole.SERVER_PROPERTY,
    havingValue = "true",
    matchIfMissing = true
)
public class ServerRoleConfiguration {
    // Marker. Bean factories can be added here as server-only components are migrated.
}
