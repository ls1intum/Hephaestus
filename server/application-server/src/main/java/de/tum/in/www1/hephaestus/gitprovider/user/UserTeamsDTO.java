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
    @NonNull Set<TeamSummaryDTO> teams,
    boolean hidden
) {
    /**
     * Creates a UserTeamsDTO from a User entity using scope-specific settings.
     *
     * <p>This method applies scope-specific visibility settings,
     * enabling different configurations for the same team across multiple scopes.
     *
     * @param user the user entity
     * @param hiddenTeamIds set of team IDs that are hidden in this scope
     * @return the DTO with scope-specific settings applied
     */
    public static UserTeamsDTO fromUserWithScopeSettings(User user, Set<Long> hiddenTeamIds) {
        return fromUserWithScopeSettings(user, hiddenTeamIds, false);
    }

    /**
     * Creates a UserTeamsDTO from a User entity using scope-specific settings.
     *
     * @param user the user entity
     * @param hiddenTeamIds set of team IDs that are hidden in this scope
     * @param hidden whether this member is hidden from the leaderboard
     * @return the DTO with scope-specific settings applied
     */
    public static UserTeamsDTO fromUserWithScopeSettings(User user, Set<Long> hiddenTeamIds, boolean hidden) {
        return new UserTeamsDTO(
            user.getId(),
            user.getLogin(),
            user.getEmail(),
            user.getName() != null ? user.getName() : user.getLogin(),
            user.getHtmlUrl(),
            user
                .getTeamMemberships()
                .stream()
                .map(m -> m.getTeam())
                .filter(t -> t != null)
                .map(team -> TeamSummaryDTO.fromTeamWithScopeSettings(team, hiddenTeamIds.contains(team.getId())))
                .collect(Collectors.toCollection(LinkedHashSet::new)),
            hidden
        );
    }
}
