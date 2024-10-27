package de.tum.in.www1.hephaestus.gitprovider.team;

import org.springframework.lang.NonNull;

public record TeamInfoDTO(@NonNull Long id, @NonNull String name, @NonNull String color) {
    public static TeamInfoDTO fromTeam(Team team) {
        return new TeamInfoDTO(team.getId(), team.getName(), team.getColor());
    }
}
