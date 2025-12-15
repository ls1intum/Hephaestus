package de.tum.in.www1.hephaestus.gitprovider.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserInfoDTO(
    @NonNull Long id,
    @NonNull String login,
    String email,
    @NonNull String avatarUrl,
    @NonNull String name,
    @NonNull String htmlUrl,
    int leaguePoints,
    String slackUserId
) {
    public static UserInfoDTO fromUser(User user) {
        return fromUser(user, 0);
    }

    public static UserInfoDTO fromUser(User user, int leaguePoints) {
        return new UserInfoDTO(
            user.getId(),
            user.getLogin(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getName(),
            user.getHtmlUrl(),
            leaguePoints,
            user.getSlackUserId()
        );
    }
}
