package de.tum.in.www1.hephaestus.gitprovider.installationtarget;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "installation_target")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class InstallationTarget extends BaseGitServiceEntity {

    @Column(name = "login", nullable = false, length = 255)
    private String login;

    @Column(name = "node_id", length = 255)
    private String nodeId;

    @Column(name = "html_url", length = 512)
    private String htmlUrl;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32)
    private TargetType type;

    @Column(name = "site_admin")
    private Boolean siteAdmin;

    @Column(name = "is_verified")
    private Boolean verified;

    @Column(name = "has_organization_projects")
    private Boolean hasOrganizationProjects;

    @Column(name = "has_repository_projects")
    private Boolean hasRepositoryProjects;

    @Column(name = "public_repos")
    private Integer publicRepos;

    @Column(name = "public_gists")
    private Integer publicGists;

    @Column(name = "followers")
    private Integer followers;

    @Column(name = "following")
    private Integer following;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "last_renamed_at")
    private Instant lastRenamedAt;

    @Column(name = "last_renamed_from", length = 255)
    private String lastRenamedFrom;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @OneToMany(mappedBy = "target")
    @ToString.Exclude
    private Set<Installation> installations = new HashSet<>();

    public enum TargetType {
        USER,
        ORGANIZATION,
        ENTERPRISE,
        BOT,
        UNKNOWN;

        public static TargetType fromValue(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            return switch (value.toUpperCase()) {
                case "USER" -> USER;
                case "ORGANIZATION" -> ORGANIZATION;
                case "ENTERPRISE" -> ENTERPRISE;
                case "BOT" -> BOT;
                default -> UNKNOWN;
            };
        }
    }
    /*
     * Supported webhook fields/relationships (GHEventPayload.Installation, REST `installation`; installation_target webhook):
     * Fields:
     * - installation.account.id → BaseGitServiceEntity `id`
     * - installation.account.created_at / updated_at → `createdAt` / `updatedAt`
     * - installation.account.login → `login`
     * - installation.account.node_id → `nodeId`
     * - installation.account.html_url → `htmlUrl`
     * - installation.account.avatar_url → `avatarUrl`
     * - installation.account.type / target_type → `type`
     * - installation.account.site_admin → `siteAdmin`
     * - installation_target.account.description → `description`
     * - installation_target.account.is_verified → `verified`
     * - installation_target.account.has_organization_projects / has_repository_projects → respective booleans
     * - installation_target.account.public_repos / public_gists / followers / following → counters
     * - installation_target.account.archived_at → `archivedAt`
     * Relationships:
     * - installation.account ↔ `installations` (via Installation#target)
     *
     * Ignored (available from hub4j 2.0-rc.5 without extra fetch):
     * Fields:
     * - installation.account.url / repos_url / events_url / *_url link relations (not persisted; reachable through GitHub APIs on demand)
     * - account blog/company/email/location/twitter metadata (handled by dedicated user/org sync)
     * Relationships:
     * - Followers / members collections. Persisting them requires additional pagination logic outside webhook scope.
     *
     * Desired but missing in hub4j 2.0-rc.5 (present in GitHub REST/GraphQL):
     * Fields:
     * - REST `billing_email`, `default_repository_permission`, `members_allowed_repository_creation_type`
     * - GraphQL `Organization.plan`, `Organization.ipAllowListEntries`
     * Relationships:
     * - GraphQL `enterpriseOwnerInfo` (ties to future GitLab parity)
     *
     * Requires extra REST/GraphQL fetch (deferred):
     * - Member rosters (`GET /orgs/{org}/members`), team structures, verified domains, SAMl/SCIM configuration
     * - Organization audit log metadata (GraphQL `OrganizationAuditEntryConnection`).
     */
}
