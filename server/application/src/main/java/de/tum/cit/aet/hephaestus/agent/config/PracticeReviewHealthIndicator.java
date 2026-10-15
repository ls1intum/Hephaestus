package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.job.AgentProperties;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class PracticeReviewHealthIndicator implements HealthIndicator {

    private final AgentProperties agentProperties;
    private final boolean gitEnabled;
    private final ClasspathSourceCatalogRegistry sourceCatalog;
    private final Clock clock;
    private final boolean workerRole;

    public PracticeReviewHealthIndicator(
            AgentProperties agentProperties,
            @Value("${hephaestus.git.enabled:false}") boolean gitEnabled,
            ClasspathSourceCatalogRegistry sourceCatalog,
            Clock clock,
            @Value("${" + RuntimeRole.WORKER_PROPERTY + ":true}") boolean workerRole) {
        this.agentProperties = agentProperties;
        this.gitEnabled = gitEnabled;
        this.sourceCatalog = sourceCatalog;
        this.clock = clock;
        this.workerRole = workerRole;
    }

    @Override
    public Health health() {
        if (!workerRole || !agentProperties.enabled()) {
            return Health.up().withDetail("reviewsEnabled", false).build();
        }
        if (!gitEnabled) {
            return Health.outOfService()
                    .withDetail("reviewsEnabled", true)
                    .withDetail("reason", "GIT_CHECKOUT_DISABLED")
                    .build();
        }
        if (sourceCatalog
                .earliestUseDecisionExpiry(SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)
                .filter(expiry -> !expiry.isAfter(clock.instant()))
                .isPresent()) {
            return Health.outOfService()
                    .withDetail("reviewsEnabled", true)
                    .withDetail("reason", "SOURCE_AUTHORIZATION_EXPIRED")
                    .build();
        }
        return Health.up().withDetail("reviewsEnabled", true).build();
    }
}
