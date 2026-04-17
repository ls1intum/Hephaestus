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
 *   <li>Email local-part match: extracts the portion before {@code @} and matches via
 *       {@link UserRepository#findByLoginAndProviderId}. Covers TUM/LRZ conventions
 *       like {@code ge27coy@mytum.de} or {@code ga84xah@mytum.de} where the local-part
 *       equals the GitLab login. Skipped for {@code firstname.lastname@domain} style
 *       addresses (any local-part containing {@code .} or {@code +} is ignored).</li>
 *   <li>Login-based match via {@link UserRepository#findByLogin} (for webhook data that provides
 *       the username directly)</li>
 * </ol>
 * <p>
 * This component is shared between {@code GitHubCommitBackfillService} (sync-cycle backfill),
 * {@code GitHubPushMessageHandler} (webhook push handling), and the equivalent GitLab paths
 * to ensure consistent author resolution across all commit ingestion paths.
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

    /**
     * Matches email local-parts that look like a single login (no dot, no plus).
     * Used for Strategy 3 to avoid false positives on {@code firstname.lastname@tum.de}
     * style addresses where the local-part is not a login.
     */
    private static final Pattern LOGIN_LIKE_LOCAL_PART = Pattern.compile("^[A-Za-z0-9_-]+$");

    /**
     * Matches GitLab bot noreply email patterns of the form
     * {@code group_{id}_bot_{hash}@noreply.<host>} or
     * {@code project_{id}_bot_{hash}@noreply.<host>}. These cannot be resolved to a user.
     */
    private static final Pattern GITLAB_BOT_NOREPLY_PATTERN = Pattern.compile(
        "^(?:group|project)_\\d+_bot[_a-z0-9-]*@noreply\\..+$",
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

        // Strategy 3: email local-part as login (TUM/LRZ pattern ge27coy@mytum.de → "ge27coy").
        // Skips bot noreply addresses and local-parts containing dots/plus so that
        // firstname.lastname@tum.de or user+tag@domain do not produce false matches.
        if (GITLAB_BOT_NOREPLY_PATTERN.matcher(email).matches()) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            String localPart = email.substring(0, atIndex);
            if (LOGIN_LIKE_LOCAL_PART.matcher(localPart).matches()) {
                if (providerId != null) {
                    return userRepository.findByLoginAndProviderId(localPart, providerId).map(User::getId).orElse(null);
                }
                return userRepository.findByLogin(localPart).map(User::getId).orElse(null);
            }
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
