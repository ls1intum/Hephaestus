package de.tum.in.www1.hephaestus.gitprovider.user.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/**
 * Classifies GitLab users by login pattern.
 * <p>
 * GitLab group and project access tokens materialise as users with deterministic
 * logins of the form {@code group_<id>_bot_<hash>} or {@code project_<id>_bot_<hash>}
 * (hex-encoded). These are service identities, not humans — marking them as
 * {@link User.Type#BOT} so that downstream filters (team rosters, league points,
 * leaderboard) can exclude them uniformly.
 */
public final class GitLabUserClassifier {

    private static final Pattern BOT_LOGIN_PATTERN = Pattern.compile("^(group|project)_\\d+_bot_[0-9a-f]+$");

    private GitLabUserClassifier() {}

    public static boolean isBot(@Nullable String login) {
        return login != null && BOT_LOGIN_PATTERN.matcher(login).matches();
    }

    public static User.Type classify(@Nullable String login) {
        return isBot(login) ? User.Type.BOT : User.Type.USER;
    }
}
