package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "repository_collaborator")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RepositoryCollaborator {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    @ToString.Exclude
    private Repository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 32, nullable = false)
    private Permission permission = Permission.UNKNOWN;

    public RepositoryCollaborator(Repository repository, User user, Permission permission) {
        this.repository = repository;
        this.user = user;
        this.permission = permission == null ? Permission.UNKNOWN : permission;
        this.id.setRepositoryId(repository.getId());
        this.id.setUserId(user.getId());
    }

    public void updatePermission(Permission permission) {
        if (permission != null && permission != Permission.UNKNOWN) {
            this.permission = permission;
        }
    }

    public enum Permission {
        READ,
        TRIAGE,
        WRITE,
        MAINTAIN,
        ADMIN,
        UNKNOWN;

        public static Permission fromGitHubValue(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "read", "pull" -> READ;
                case "triage" -> TRIAGE;
                case "write", "push" -> WRITE;
                case "maintain" -> MAINTAIN;
                case "admin" -> ADMIN;
                default -> UNKNOWN;
            };
        }
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "repository_id", nullable = false)
        private Long repositoryId;

        @Column(name = "user_id", nullable = false)
        private Long userId;
    }
    /*
     * Webhook coverage (member event → repository collaborators):
     * Supported (webhook, no extra fetch):
     * - member.login/id/avatar_url/type ⇒ persisted in User and linked through this join entity.
     * - repository.id/full_name/private/html_url ⇒ persisted in Repository and linked here.
     * - changes.permission.{from,to} / changes.role_name.{from,to} ⇒ mapped to Permission with MAINTAIN/TRIAGE support.
     * Ignored although exposed by hub4j payloads:
     * - member.site_admin/user_view_type, repository.custom_properties/topics. Reason: analytics-only signals.
     * - changes.something_else (hub4j surfaces `role_name` duplicate) ⇒ redundant with permission translation.
     * Desired but missing in hub4j/github-api 2.0-rc.5 (available via REST/GraphQL):
     * - REST GET /repos/{owner}/{repo}/collaborators/{username}/permission → permission_sources, inherited team roles.
     * - GraphQL RepositoryCollaboratorPermissionEdge.permission (full enum incl. MAINTAIN/TRIAGE) & RepositoryInvitation.ssoAuthorization.
     * - REST GET /repos/{owner}/{repo}/outside_collaborators → pending/outside collaborator state.
     * Requires extra fetch (out-of-scope for now):
     * - GET /repos/{owner}/{repo}/invitations for invitation metadata & expiration timestamps.
     * - GET /orgs/{org}/memberships/{username} for audit fields such as role_last_updated.
     */
}
