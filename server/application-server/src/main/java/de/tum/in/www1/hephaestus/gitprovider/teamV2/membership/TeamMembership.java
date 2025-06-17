package de.tum.in.www1.hephaestus.gitprovider.teamV2.membership;

import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "team_v2_membership")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TeamMembership {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    private TeamV2 team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    private User user;

    @Enumerated(EnumType.STRING)
    private Role role;

    public TeamMembership(TeamV2 team, User user, Role role) {
        this.team = team;
        this.user = user;
        this.role = role;
        this.id.setTeamId(team.getId());
        this.id.setUserId(user.getId());
    }

    public enum Role {
        MEMBER,
        MAINTAINER,
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private Long teamId;
        private Long userId;
    }

}
