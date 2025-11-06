package de.tum.in.www1.hephaestus.gitprovider.team.membership;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team_membership")
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
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    private User user;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private Role role;

    public TeamMembership(Team team, User user, Role role) {
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
/*
 * Webhook coverage (membership event → team members):
 * Supported (webhook, no extra fetch):
 * - membership.member.login/id/avatar_url ⇒ persisted in User and associated via this join entity.
 * - membership.team.id/name/html_url/privacy ⇒ persisted in Team and referenced here.
 * - membership.action added/removed ⇒ inserts or deletes TeamMembership rows (role defaults to MEMBER; webhook omits role).
 * Ignored although hub4j exposes:
 * - payload.scope (always "team") and payload.organization.login ⇒ redundant with Team.organization.
 * - member.site_admin/type ⇒ not required for role resolution.
 * Desired but missing in hub4j/github-api 2.0-rc.5 (available via REST/GraphQL):
 * - REST membership.member_role / GraphQL TeamMemberRole (maintainer vs member distinction).
 * - REST membership.state / GraphQL TeamMemberEdge.state (pending vs active).
 * Requires extra fetch (out-of-scope for now):
 * - GET /orgs/{org}/teams/{team_slug}/memberships/{username} for inviter, last_modified_at, SSO metadata.
 * - GET /orgs/{org}/outside_collaborators to reconcile outside contributor access.
 */
