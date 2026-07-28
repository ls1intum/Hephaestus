package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import org.jspecify.annotations.Nullable;

/**
 * The tenancy key for a workspace's synced teams: {@code (accountLogin, providerId)}.
 * <p>
 * {@code team} carries no {@code workspace_id}, so team reads and authorization scope on this pair —
 * the same pair teams are stamped with at sync time, and the {@code (provider_id, organization)}
 * prefix of {@code uk_team_provider_organization_slug}. Matching on the org login alone leaks
 * between tenants that share an {@code account_login} on different providers. Holding both halves as
 * one value makes dropping one of them unrepresentable.
 *
 * @see WorkspaceTeamScopeResolver
 */
public record WorkspaceTeamScope(String accountLogin, Long providerId) {
    /** Whether {@code team} belongs to this workspace. */
    public boolean contains(@Nullable Team team) {
        return (
            team != null &&
            team.getOrganization() != null &&
            team.getOrganization().equalsIgnoreCase(accountLogin) &&
            team.getProvider() != null &&
            providerId.equals(team.getProvider().getId())
        );
    }
}
