package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import jakarta.persistence.CascadeType;
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
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Team extends BaseGitServiceEntity {

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private Privacy privacy;

    private String organization;

    @Column(length = 512)
    private String htmlUrl;

    private Long parentId;

    /**
     * Timestamp of the last successful sync for this team from the Git provider.
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this team
     * was last synchronized via GraphQL. Used to implement sync cooldown logic
     * and detect stale data.
     *
     * @see de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService
     */
    private Instant lastSyncAt;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<TeamRepositoryPermission> repoPermissions = new HashSet<>();

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<TeamMembership> memberships = new HashSet<>();

    public void addMembership(TeamMembership membership) {
        memberships.add(membership);
        membership.setTeam(this);
    }

    public void removeMembership(TeamMembership membership) {
        memberships.remove(membership);
        membership.setTeam(null);
    }

    public void addRepoPermission(TeamRepositoryPermission permission) {
        repoPermissions.add(permission);
        permission.setTeam(this);
    }

    public void clearAndAddRepoPermissions(Set<TeamRepositoryPermission> fresh) {
        repoPermissions.clear();
        fresh.forEach(this::addRepoPermission);
    }

    /**
     * Team privacy level. Maps directly to GitHub GraphQL TeamPrivacy enum.
     *
     * @see <a href="https://docs.github.com/en/graphql/reference/enums#teamprivacy">GitHub TeamPrivacy</a>
     */
    public enum Privacy {
        /** Only organization members can view or request access. */
        SECRET,
        /** Visible to all members of the organization (GitHub calls this VISIBLE). */
        VISIBLE,
    }
    // Ignored GitHub properties:
    // - nodeId
    // - slug
    // - apiUrl               (API URL for this team)
    // - members_url       (templated URL for member listing)
    // - repositories_url  (templated URL for repos listing)
    // - parent            (if this team has a parent team)
    // - permissions       (maps to our repoPermissions, but scoped to the OAuth user)
    // - members_count     (cached count; we page through listMembers())
    // - repos_count       (cached count; we page through listRepositories())
    // - privacy_level     (older name for privacy)
}
