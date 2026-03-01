package de.tum.in.www1.hephaestus.gitprovider.commit;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves git commit author/committer identities to database {@link User} records.
 * <p>
 * Resolution strategy (in order):
 * <ol>
 *   <li>Direct email match via {@link UserRepository#findByEmail} (case-insensitive)</li>
 *   <li>GitHub noreply email parsing: extracts login from {@code username@users.noreply.github.com}
 *       or {@code 12345+username@users.noreply.github.com} patterns, then matches via
 *       {@link UserRepository#findByLogin}</li>
 *   <li>Login-based match via {@link UserRepository#findByLogin} (for webhook data that provides
 *       the GitHub username directly)</li>
 * </ol>
 * <p>
 * This component is shared between {@code GitHubCommitBackfillService} (sync-cycle backfill)
 * and {@code GitHubPushMessageHandler} (webhook push handling) to ensure consistent author
 * resolution across all commit ingestion paths.
 */
@Component
@RequiredArgsConstructor
public class CommitAuthorResolver {

    /**
     * Matches GitHub noreply email patterns:
     * <ul>
     *   <li>{@code username@users.noreply.github.com}</li>
     *   <li>{@code 12345+username@users.noreply.github.com} (ID-prefixed)</li>
     * </ul>
     */
    private static final Pattern GITHUB_NOREPLY_PATTERN = Pattern.compile(
        "^(?:\\d+\\+)?([^@]+)@users\\.noreply\\.github\\.com$",
        Pattern.CASE_INSENSITIVE
    );

    private final UserRepository userRepository;

    /**
     * Resolve a user's database ID by email, with GitHub noreply fallback.
     * Scoped to a specific provider to avoid cross-provider ambiguity.
     *
     * @param email      the git author/committer email
     * @param providerId the provider to scope the lookup to
     * @return the user's database ID, or {@code null} if no match is found
     */
    @Nullable
    public Long resolveByEmail(@Nullable String email, @Nullable Long providerId) {
        if (email == null || email.isBlank()) {
            return null;
        }

        // Strategy 1: direct email match (provider-scoped if available)
        Long id;
        if (providerId != null) {
            id = userRepository.findByEmailAndProviderId(email, providerId).map(User::getId).orElse(null);
        } else {
            id = userRepository.findByEmail(email).map(User::getId).orElse(null);
        }
        if (id != null) {
            return id;
        }

        // Strategy 2: parse GitHub noreply email → extract login → match by login
        String login = extractLoginFromNoreply(email);
        if (login != null) {
            if (providerId != null) {
                return userRepository.findByLoginAndProviderId(login, providerId).map(User::getId).orElse(null);
            }
            return userRepository.findByLogin(login).map(User::getId).orElse(null);
        }

        return null;
    }

    /**
     * Resolve a user's database ID by login (username).
     * Scoped to a specific provider to avoid cross-provider ambiguity.
     *
     * @param login      the username (e.g., from webhook {@code CommitUser.username})
     * @param providerId the provider to scope the lookup to
     * @return the user's database ID, or {@code null} if not found
     */
    @Nullable
    public Long resolveByLogin(@Nullable String login, @Nullable Long providerId) {
        if (login == null || login.isBlank()) {
            return null;
        }
        if (providerId != null) {
            return userRepository.findByLoginAndProviderId(login, providerId).map(User::getId).orElse(null);
        }
        return userRepository.findByLogin(login).map(User::getId).orElse(null);
    }

    /**
     * Extracts the GitHub login from a noreply email address.
     *
     * @param email the email to parse
     * @return the extracted login, or {@code null} if the email doesn't match noreply patterns
     */
    @Nullable
    static String extractLoginFromNoreply(String email) {
        Matcher matcher = GITHUB_NOREPLY_PATTERN.matcher(email);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
