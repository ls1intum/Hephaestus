package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.Team.Privacy;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import org.springframework.lang.NonNull;

@Schema(description = "Detailed information about a team including members, repositories, and labels")
public record TeamInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the team") Long id,
    @NonNull @Schema(description = "Name of the team") String name,
    @Schema(description = "ID of the parent team, if this is a sub-team") Long parentId,
    @Schema(description = "Description of the team") String description,
    @Schema(description = "Privacy level of the team (SECRET or VISIBLE)") Privacy privacy,
    @Schema(description = "Organization the team belongs to") String organization,
    @Schema(description = "URL to the team's page on the git provider") String htmlUrl,
    @NonNull @Schema(description = "Whether the team is hidden from leaderboard display") Boolean hidden,
    @NonNull @Schema(description = "Repositories the team has access to") List<RepositoryInfoDTO> repositories,
    @NonNull @Schema(description = "Labels configured as filters for this team") List<LabelInfoDTO> labels,
    @NonNull @Schema(description = "Members of the team (excluding bots)") List<UserInfoDTO> members,
    @NonNull @Schema(description = "Total count of team memberships") Integer membershipCount,
    @NonNull @Schema(description = "Count of repository permissions") Integer repoPermissionCount
) {
    /**
     * Creates a TeamInfoDTO from a Team entity using workspace-scoped settings.
     *
     * <p>This method applies workspace-specific visibility and label settings,
     * enabling different configurations for the same team across multiple workspaces.
     *
     * @param team the team entity
     * @param isHidden whether the team is hidden in this workspace
     * @param workspaceLabels labels configured as filters for this team in this workspace
     * @param hiddenRepoIds repository IDs hidden from contributions in this workspace
     * @return the DTO with workspace-scoped settings applied
     */
    public static TeamInfoDTO fromTeamWithWorkspaceSettings(
        Team team,
        boolean isHidden,
        Set<Label> workspaceLabels,
        Set<Long> hiddenRepoIds
    ) {
        return new TeamInfoDTO(
            team.getId(),
            team.getName(),
            team.getParentId(),
            team.getDescription(),
            team.getPrivacy(),
            team.getOrganization(),
            team.getHtmlUrl(),
            isHidden,
            team
                .getRepoPermissions()
                .stream()
                .map(permission ->
                    RepositoryInfoDTO.fromPermissionWithWorkspaceSettings(
                        permission,
                        hiddenRepoIds.contains(permission.getRepository().getId())
                    )
                )
                .toList(),
            workspaceLabels.stream().map(LabelInfoDTO::fromLabel).toList(),
            team
                .getMemberships()
                .stream()
                .map(m -> m.getUser())
                .filter(u -> u != null && !User.Type.BOT.equals(u.getType()))
                .map(UserInfoDTO::fromUser)
                .toList(),
            team.getMemberships().size(),
            team.getRepoPermissions().size()
        );
    }
}
