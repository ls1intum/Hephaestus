package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@Table(
    name = "organization",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_organization_github_id", columnNames = "github_id"),
        @UniqueConstraint(name = "uq_organization_login", columnNames = "login"),
    }
)
public class Organization extends BaseGitServiceEntity {

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(name = "login", nullable = false)
    private String login;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "installation_id")
    private Long installationId;

    @OneToOne(mappedBy = "organization")
    @ToString.Exclude
    private Workspace workspace;
    /*
     * Webhook coverage (organization events → organization metadata):
     * Supported (webhook, no extra fetch):
     * - organization.id/login ⇒ persisted as githubId/id and login.
     * - organization.avatar_url/html_url ⇒ stored in avatarUrl/htmlUrl when present.
     * - installation.id (installation event) ⇒ stored as installationId for GitHub App installations.
     * - installation_target.login rename keeps login aligned with Workspace/account data.
     * Ignored although hub4j exposes them from payloads:
     * - organization.description/company/blog/location/email/twitter_username/is_verified.
     * - organization.has_organization_projects/has_repository_projects/public_repos/public_gists/followers/following/type/node_id.
     *   Rationale: telemetry-only attributes that do not contribute to ETL targets or permissions.
     * Desired but missing in hub4j/github-api 2.0-rc.5 (available via REST/GraphQL):
     * - REST `GET /orgs/{org}` → plan.*, members_can_create_repositories, default_repository_permission.
     * - GraphQL `Organization.ipAllowListEntries`, `Organization.samlIdentityProvider`, `Organization.requireSshSignedCommits`.
     * Requires extra fetch (out-of-scope for now):
     * - organization.name/billing_email/two_factor_requirement_enabled (requires REST organization detail request).
     * - Audit surfaces such as `GET /orgs/{org}/audit-log` or GraphQL `Organization.pinnedItems`.
     */
}
