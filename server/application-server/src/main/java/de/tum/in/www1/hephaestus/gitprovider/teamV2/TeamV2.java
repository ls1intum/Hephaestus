package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.hephaestus.gitprovider.teamV2.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.permission.TeamRepositoryPermission;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "team_v2")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class TeamV2 extends BaseGitServiceEntity {

    private String name;

    private String slug;

    private String description;

    private String privacy;

    private String githubOrganization;

    private OffsetDateTime lastSyncedAt;

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

    public void addRepoPermission(TeamRepositoryPermission permission) {
        repoPermissions.add(permission);
        permission.setTeam(this);
    }

    public void clearAndAddRepoPermissions(Set<TeamRepositoryPermission> fresh) {
        repoPermissions.clear();
        fresh.forEach(this::addRepoPermission);
    }
}
