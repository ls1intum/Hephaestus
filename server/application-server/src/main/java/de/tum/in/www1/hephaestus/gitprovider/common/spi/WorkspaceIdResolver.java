package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Resolves workspace ID from organization login for webhook processing.
 */
public interface WorkspaceIdResolver {
    Optional<Long> findWorkspaceIdByOrgLogin(String organizationLogin);
}
