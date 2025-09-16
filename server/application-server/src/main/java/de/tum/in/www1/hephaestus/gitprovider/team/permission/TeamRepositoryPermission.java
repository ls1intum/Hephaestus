package de.tum.in.www1.hephaestus.gitprovider.team.permission;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team_repository_permission")
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
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    private Repository repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PermissionLevel permission;

    public TeamRepositoryPermission(Team team, Repository repository, PermissionLevel permission) {
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
