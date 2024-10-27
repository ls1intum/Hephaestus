package de.tum.in.www1.hephaestus.gitprovider.team;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamDTO(@NonNull Long id, @NonNull String name, @NonNull String color) {
    public static TeamDTO fromTeam(Team team) {
        return new TeamDTO(team.getId(), team.getName(), team.getColor());
    }
}
