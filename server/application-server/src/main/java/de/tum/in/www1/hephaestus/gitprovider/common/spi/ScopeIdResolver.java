package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.util.Optional;

/**
 * Resolves scope ID (workspace ID) for webhook processing and sync operations.
 * <p>
 * This SPI allows the gitprovider module to resolve scope IDs without depending
 * on workspace domain entities directly.
 *
 * <h2>Resolution Strategies</h2>
 * <ul>
 *   <li>{@link #findScopeIdByOrgLogin(String)} - For organization-owned repositories</li>
 *   <li>{@link #findScopeIdByRepositoryName(String)} - For personal (user-owned) repositories
 *       and fallback when org lookup fails</li>
 * </ul>
 *
 * <h2>Personal Repository Support</h2>
 * Personal repositories (owned by GitHub users, not organizations) have no organization
 * entity. For these repos, scope resolution falls back to looking up the workspace
 * by the repository's nameWithOwner field, which is tracked in the workspace's
 * monitored repositories list.
 */
public interface ScopeIdResolver {
    /**
     * Resolve scope ID by organization login.
     * <p>
     * This is the primary resolution method for organization-owned repositories.
     *
     * @param organizationLogin the GitHub organization login (e.g., "ls1intum")
     * @return the scope ID if found, empty otherwise
     */
    Optional<Long> findScopeIdByOrgLogin(String organizationLogin);

    /**
     * Resolve scope ID by repository nameWithOwner.
     * <p>
     * This method supports personal (user-owned) repositories that have no organization.
     * It looks up the workspace that has this repository in its monitored repositories list.
     * <p>
     * This is also used as a fallback when organization-based lookup fails, ensuring
     * activity events are tracked for all repository types.
     *
     * @param repositoryNameWithOwner the full repository name (e.g., "octocat/hello-world")
     * @return the scope ID if found, empty otherwise
     */
    Optional<Long> findScopeIdByRepositoryName(String repositoryNameWithOwner);
}
