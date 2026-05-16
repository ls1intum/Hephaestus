package de.tum.in.www1.hephaestus.agent.mentor.chat.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Operator-asserted sticky-routing guard for multi-replica mentor deployments.
 *
 * <p><b>Why this exists.</b> Mentor chat keeps a single in-flight turn per
 * {@code (workspaceId, contributorId, threadId)} alive across HTTP requests by routing every
 * follow-up SSE GET to the same JVM that owns the interactive sandbox. The DB does enforce
 * single-flight via the partial unique index {@code ux_chat_message_in_flight_v2}, but that
 * fires <em>after</em> the second replica has already created an orphan {@code IN_FLIGHT}
 * row and failed with a 409. To prevent that latency-and-orphan-row failure mode entirely,
 * the deployment must route requests to a stable replica (Traefik sticky cookie, AWS ALB
 * stickiness, or equivalent).
 *
 * <p>Spring Boot has no way to introspect the upstream load balancer's routing policy. We
 * therefore make sticky routing an <b>operator assertion</b>: when
 * {@code hephaestus.mentor.expected-replicas > 1}, the deployment must also set
 * {@code hephaestus.mentor.sticky-routing-confirmed=true}. If both conditions are not met,
 * this readiness check trips DOWN and Kubernetes (or Coolify) refuses to route traffic to the
 * pod — turning a silent correctness bug into a loud deploy failure.
 *
 * <p>The reverse case (single replica without the flag) is the development default and reports
 * UP — there is no affinity hazard with one replica.
 */
@Component
public class MentorReplicaAffinityCheck implements HealthIndicator {

    /** Default for {@code hephaestus.mentor.expected-replicas}: single replica. */
    static final int DEFAULT_EXPECTED_REPLICAS = 1;

    @Nullable
    private final String hostname;

    private final int expectedReplicas;
    private final boolean stickyRoutingConfirmed;

    public MentorReplicaAffinityCheck(
        @Value("${HOSTNAME:}") String hostname,
        @Value("${hephaestus.mentor.expected-replicas:" + DEFAULT_EXPECTED_REPLICAS + "}") int expectedReplicas,
        @Value("${hephaestus.mentor.sticky-routing-confirmed:false}") boolean stickyRoutingConfirmed
    ) {
        this.hostname = (hostname == null || hostname.isBlank()) ? null : hostname;
        this.expectedReplicas = expectedReplicas;
        this.stickyRoutingConfirmed = stickyRoutingConfirmed;
    }

    @Override
    public Health health() {
        if (expectedReplicas > 1 && !stickyRoutingConfirmed) {
            return Health.down()
                .withDetail("reason", "multi-replica without operator-asserted sticky routing")
                .withDetail("expectedReplicas", expectedReplicas)
                .withDetail("stickyRoutingConfirmed", false)
                .withDetail("hostname", hostname == null ? "unknown" : hostname)
                .build();
        }
        return Health.up()
            .withDetail("hostname", hostname == null ? "unknown" : hostname)
            .withDetail("expectedReplicas", expectedReplicas)
            .withDetail("stickyRoutingConfirmed", stickyRoutingConfirmed)
            .build();
    }
}
