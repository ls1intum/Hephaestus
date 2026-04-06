package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * DTO representing a workspace membership with user information.
 */
@Schema(description = "A user's membership in a workspace")
public record WorkspaceMembershipDTO(
    @Schema(description = "Unique identifier of the user") Long userId,
    @Schema(description = "Login/username of the user") String userLogin,
    @Schema(description = "Display name of the user") String userName,
    @Schema(description = "Role of the user in this workspace (OWNER, ADMIN, MEMBER)") WorkspaceRole role,
    @Schema(description = "League points earned by the user in this workspace", example = "150") int leaguePoints,
    @Schema(description = "Timestamp when the membership was created") Instant createdAt,
    @Schema(description = "Whether the member is hidden from the leaderboard") boolean hidden
) {
    public static WorkspaceMembershipDTO from(WorkspaceMembership membership) {
        return from(membership, membership.getRole());
    }

    public static WorkspaceMembershipDTO from(WorkspaceMembership membership, WorkspaceRole effectiveRole) {
        var user = membership.getUser();
        return new WorkspaceMembershipDTO(
            user.getId(),
            user.getLogin(),
            user.getName(),
            effectiveRole,
            membership.getLeaguePoints(),
            membership.getCreatedAt(),
            membership.isHidden()
        );
    }
}
