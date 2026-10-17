package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings;

import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "workspace_team_repository_settings")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class WorkspaceTeamRepositorySettings {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("workspaceId")
    @JoinColumn(
            name = "workspace_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_team_repo_settings_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    @JoinColumn(
            name = "team_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_team_repo_settings_team"))
    @ToString.Exclude
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_team_repo_settings_repository"))
    @ToString.Exclude
    private Repository repository;

    @Column(name = "hidden_from_contributions", nullable = false)
    private boolean hiddenFromContributions = false;

    public WorkspaceTeamRepositorySettings(Workspace workspace, Team team, Repository repository) {
        this.workspace = workspace;
        this.team = team;
        this.repository = repository;
        this.id = new Id(workspace.getId(), team.getId(), repository.getId());
    }

    /**
     * Composite primary key for workspace team repository settings.
     *
     * <p>Ensures a team-repository pair can have at most one settings record per workspace. All
     * fields are populated via {@code @MapsId} from the entity relationships.
     */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "workspace_id")
        private Long workspaceId;

        @Column(name = "team_id")
        private Long teamId;

        @Column(name = "repository_id")
        private Long repositoryId;
    }
}
