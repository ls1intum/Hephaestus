package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.time.Instant;

/**
 * DTO representing a workspace membership with user information.
 */
public record WorkspaceMembershipDTO(
    Long userId,
    String userLogin,
    String userName,
    WorkspaceRole role,
    int leaguePoints,
    Instant createdAt
) {
    public static WorkspaceMembershipDTO from(WorkspaceMembership membership) {
        var user = membership.getUser();
        return new WorkspaceMembershipDTO(
            user.getId(),
            user.getLogin(),
            user.getName(),
            membership.getRole(),
            membership.getLeaguePoints(),
            membership.getCreatedAt()
        );
    }
}
