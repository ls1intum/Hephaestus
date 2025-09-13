package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
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

    @Column(columnDefinition = "TEXT")
    private String htmlUrl;

    private Long parentId;

    private OffsetDateTime lastSyncedAt;

    /**
     * Hephaestus field. Controls whether the team is hidden in the overview.
     */
    private boolean hidden = false;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<TeamRepositoryPermission> repoPermissions = new HashSet<>();

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<TeamMembership> memberships = new HashSet<>();

    /**
     * Hephaestus-managed labels assigned to this team (admin-managed only).
     *
     * Used for filtering team-specific contributions.
     */
    @ManyToMany
    @JoinTable(
        name = "team_labels",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

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

    public void addLabel(Label label) {
        labels.add(label);
    }

    public void removeLabel(Label label) {
        labels.remove(label);
    }

    public enum Privacy {
        /** Only organization members can view or request access. */
        SECRET,
        /** Visible to all members of the organization. */
        CLOSED,
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
