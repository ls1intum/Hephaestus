package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
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
    @NonNull Set<TeamInfoDTO> teams
) {
    public static UserTeamsDTO fromUser(User user) {
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
                .map(TeamInfoDTO::fromTeam)
                .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }
}
