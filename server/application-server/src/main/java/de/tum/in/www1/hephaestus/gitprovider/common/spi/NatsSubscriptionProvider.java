package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;
import java.util.Set;

/**
 * Provides NATS subscription information for workspaces.
 */
public interface NatsSubscriptionProvider {
    /** Get subscription info for a workspace. */
    Optional<NatsSubscriptionInfo> getSubscriptionInfo(Long workspaceId);

    record NatsSubscriptionInfo(Long workspaceId, Set<String> repositoryNamesWithOwner, String organizationLogin) {
        public boolean hasRepositories() {
            return repositoryNamesWithOwner != null && !repositoryNamesWithOwner.isEmpty();
        }

        public boolean hasOrganization() {
            return organizationLogin != null && !organizationLogin.isBlank();
        }
    }
}
