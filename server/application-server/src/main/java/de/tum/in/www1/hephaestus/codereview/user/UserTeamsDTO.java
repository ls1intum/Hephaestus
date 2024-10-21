package de.tum.in.www1.hephaestus.codereview.user;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.team.TeamDTO;

public record UserTeamsDTO(@NonNull Long id, @NonNull String login, @NonNull String name, @NonNull String url,
        @NonNull Set<TeamDTO> teams) {
    public static UserTeamsDTO fromUser(User user) {
        return new UserTeamsDTO(user.getId(), user.getLogin(), user.getName(), user.getUrl(),
                user.getTeams().stream().map(TeamDTO::fromTeam).collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
