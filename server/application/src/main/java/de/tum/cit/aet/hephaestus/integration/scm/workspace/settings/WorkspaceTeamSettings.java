package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings;

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
@Table(name = "workspace_team_settings")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class WorkspaceTeamSettings {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("workspaceId")
    @JoinColumn(
            name = "workspace_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspace_team_settings_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_workspace_team_settings_team"))
    @ToString.Exclude
    private Team team;

    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    public WorkspaceTeamSettings(Workspace workspace, Team team) {
        this.workspace = workspace;
        this.team = team;
        this.id = new Id(workspace.getId(), team.getId());
    }

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
    }
}
