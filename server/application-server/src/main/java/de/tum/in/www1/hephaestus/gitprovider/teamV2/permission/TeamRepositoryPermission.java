package de.tum.in.www1.hephaestus.gitprovider.teamV2.permission;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "team_v2_repository_permission")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TeamRepositoryPermission {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    private TeamV2 team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    private Repository repository;

    @Enumerated(EnumType.STRING)
    private PermissionLevel permission;

    public TeamRepositoryPermission(TeamV2 team, Repository repository, PermissionLevel permission) {
        this.team = team;
        this.repository = repository;
        this.permission = permission;
        this.id.setTeamId(team.getId());
        this.id.setRepositoryId(repository.getId());
    }

    public enum PermissionLevel {
        READ,
        WRITE,
        MAINTAIN,
        ADMIN,
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private Long teamId;
        private Long repositoryId;
    }
}
