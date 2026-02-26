package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;
import java.util.Set;

/**
 * Provides NATS subscription information for scopes.
 */
public interface NatsSubscriptionProvider {
    /** Get subscription info for a scope. */
    Optional<NatsSubscriptionInfo> getSubscriptionInfo(Long scopeId);

    record NatsSubscriptionInfo(
        Long scopeId,
        Set<String> repositoryNamesWithOwner,
        String organizationLogin,
        String natsStreamName
    ) {
        public boolean hasRepositories() {
            return repositoryNamesWithOwner != null && !repositoryNamesWithOwner.isEmpty();
        }

        public boolean hasOrganization() {
            return organizationLogin != null && !organizationLogin.isBlank();
        }

        public boolean isGitLab() {
            return "gitlab".equals(natsStreamName);
        }
    }
}
