package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamSummaryDTO;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

public record UserTeamsDTO(
    @NonNull Long id,
    @NonNull String login,
    String email,
    @NonNull String name,
    @NonNull String url,
    @NonNull Set<TeamSummaryDTO> teams
) {
    /**
     * Creates a UserTeamsDTO from a User entity using workspace-scoped settings.
     *
     * <p>This method applies workspace-specific visibility settings,
     * enabling different configurations for the same team across multiple workspaces.
     *
     * @param user the user entity
     * @param hiddenTeamIds set of team IDs that are hidden in this workspace
     * @return the DTO with workspace-scoped settings applied
     */
    public static UserTeamsDTO fromUserWithWorkspaceSettings(User user, Set<Long> hiddenTeamIds) {
        return new UserTeamsDTO(
            user.getId(),
            user.getLogin(),
            user.getEmail(),
            user.getName(),
            user.getHtmlUrl(),
            user
                .getTeamMemberships()
                .stream()
                .map(m -> m.getTeam())
                .filter(t -> t != null)
                .map(team -> TeamSummaryDTO.fromTeamWithWorkspaceSettings(team, hiddenTeamIds.contains(team.getId())))
                .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }
}
