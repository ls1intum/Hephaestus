package de.tum.in.www1.hephaestus.gitprovider.user;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserInfoDTO(
        @NonNull Long id,
        @NonNull String login,
        String email,
        @NonNull String avatarUrl,
        @NonNull String name,
        @NonNull String htmlUrl,
        int leaguePoints) {

    public static UserInfoDTO fromUser(User user) {
        return new UserInfoDTO(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getName(),
                user.getHtmlUrl(),
                user.getLeaguePoints());
    }
}
