package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for Docker daemon connectivity.
 *
 * <p>Pings the Docker daemon and reports UP/DOWN status with operational details (active container
 * count, capacity). Intended for readiness probes, not liveness — a Docker outage should prevent
 * new job acceptance but should not restart the app-server.
 */
public class DockerHealthIndicator implements HealthIndicator {

    private final SandboxContainerManager containerManager;
    private final SandboxProperties properties;

    public DockerHealthIndicator(SandboxContainerManager containerManager, SandboxProperties properties) {
        this.containerManager = containerManager;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            boolean reachable = containerManager.ping();
            if (reachable) {
                int activeContainers = containerManager.listManagedContainers().size();
                return Health.up()
                    .withDetail("dockerHost", properties.dockerHost())
                    .withDetail("activeContainers", activeContainers)
                    .withDetail("maxConcurrentContainers", properties.maxConcurrentContainers())
                    .build();
            } else {
                return Health.down()
                    .withDetail("dockerHost", properties.dockerHost())
                    .withDetail("error", "Docker daemon not reachable")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("dockerHost", properties.dockerHost())
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
