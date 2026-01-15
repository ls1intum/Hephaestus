package de.tum.in.www1.hephaestus.gitprovider.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

/**
 * Information about a user from the git provider.
 *
 * <h2>ETL Extraction Note</h2>
 * <p>
 * The {@code leaguePoints} field is a business concept from the leaderboard module
 * and does not belong in the gitprovider domain. During ETL extraction, this field
 * should be moved to a scope-specific DTO in the leaderboard module:
 * <pre>
 * public record LeaderboardUserDTO(UserInfoDTO user, int leaguePoints) {}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Information about a user from the git provider")
public record UserInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the user") Long id,
    @NonNull @Schema(description = "Login/username of the user") String login,
    @Schema(description = "Email address of the user, if public") String email,
    @NonNull @Schema(description = "URL to the user's avatar image") String avatarUrl,
    @NonNull @Schema(description = "Display name of the user") String name,
    @NonNull @Schema(description = "URL to the user's profile on the git provider") String htmlUrl,
    /**
     * League points earned by the user in the current scope.
     * <p>
     * <b>Note:</b> This field is scope-specific business logic and should be moved
     * to a leaderboard-specific DTO during ETL extraction.
     */
    @Schema(description = "League points earned by the user in the current scope", example = "150") int leaguePoints
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
            leaguePoints
        );
    }
}
