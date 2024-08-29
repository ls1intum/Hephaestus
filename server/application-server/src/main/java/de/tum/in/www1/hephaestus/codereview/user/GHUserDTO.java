package de.tum.in.www1.hephaestus.codereview.user;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GHUserDTO(String login, String email, String name, String url) {
}
