package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Resolves scope ID from organization login for webhook processing.
 */
public interface ScopeIdResolver {
    Optional<Long> findScopeIdByOrgLogin(String organizationLogin);
}
