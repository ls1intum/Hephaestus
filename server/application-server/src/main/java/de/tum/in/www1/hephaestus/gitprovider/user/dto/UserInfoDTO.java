package de.tum.in.www1.hephaestus.gitprovider.user.dto;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserInfoDTO(
        @NonNull Long id,
        @NonNull String login,
        @NonNull String avatarUrl,
        @NonNull String name,
        @NonNull String htmlUrl) {
}
