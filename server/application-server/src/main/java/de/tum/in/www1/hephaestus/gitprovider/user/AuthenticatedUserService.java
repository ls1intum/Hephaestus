package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated Keycloak principal to application {@link User} rows.
 * <p>
 * A single Keycloak account can federate multiple IdPs (GitHub + GitLab), and each IdP link
 * provisions a row in {@code "user"} keyed by {@code (provider_id, native_id)}. Identity
 * resolution therefore has two forms:
 * <ul>
 *   <li>{@link #findAllLinkedUsers()} — every row that belongs to the current principal
 *       (one per linked IdP). Use this when authorization or state needs to consider *all*
 *       linked identities, e.g. workspace memberships, which may sit on the "other" row.</li>
 *   <li>{@link #findPrimaryUser()} — the single row that best represents the principal for
 *       single-user APIs and legacy call sites. Chosen deterministically as the row with the
 *       lowest {@code id}; callers with provider-specific semantics should prefer
 *       {@link #findLinkedUserForProvider(GitProviderType)} and fail closed when it is empty.</li>
 * </ul>
 * Lookups are driven exclusively by the authoritative {@code github_id} / {@code gitlab_id}
 * JWT claims (populated by Keycloak IdP protocol mappers). There is intentionally no
 * {@code preferred_username} fallback: that claim is operator-settable and matching on it
 * across providers would let a misconfigured token resolve to an unrelated user row.
 */
@Service
@WorkspaceAgnostic("Identity resolution is scoped to the authenticated principal, not to a workspace")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedUserService.class);

    private final UserRepository userRepository;

    /**
     * Every {@link User} row linked to the current authenticated principal. Empty when no
     * identity claim is present (unauthenticated request, or a token issued before the
     * Keycloak IdP mappers were deployed).
     */
    public List<User> findAllLinkedUsers() {
        var byId = new LinkedHashMap<Long, User>();

        SecurityUtils.getCurrentGitHubId()
            .map(id -> resolveUsersForClaim(GitProviderType.GITHUB, id))
            .ifPresent(users -> users.forEach(u -> byId.putIfAbsent(u.getId(), u)));

        SecurityUtils.getCurrentGitLabId()
            .map(id -> resolveUsersForClaim(GitProviderType.GITLAB, id))
            .ifPresent(users -> users.forEach(u -> byId.putIfAbsent(u.getId(), u)));

        return new ArrayList<>(byId.values());
    }

    private List<User> resolveUsersForClaim(GitProviderType providerType, Long nativeId) {
        List<User> matches = userRepository.findAllByProviderTypeAndNativeId(providerType, nativeId);
        if (matches.size() <= 1) {
            return matches;
        }

        // The JWT only proves provider type + native id. If several rows of the same provider
        // type share that native id across different server instances, we cannot tell which one
        // belongs to the authenticated principal. Fail closed instead of unioning unrelated rows.
        log.warn(
            "Ignoring ambiguous linked-user claim: providerType={}, nativeId={}, matches={}",
            providerType,
            nativeId,
            matches
                .stream()
                .map(user ->
                    user.getProvider() != null
                        ? LoggingUtils.sanitizeForLog(user.getProvider().getServerUrl())
                        : "unknown-provider"
                )
                .toList()
        );
        return List.of();
    }

    /**
     * The {@link User} row that best represents the current principal for single-user APIs.
     * Selected deterministically as the linked row with the lowest {@code id}.
     */
    public Optional<User> findPrimaryUser() {
        List<User> linked = findAllLinkedUsers();
        if (linked.isEmpty()) {
            return Optional.empty();
        }
        return linked.stream().min(Comparator.comparing(User::getId));
    }

    /**
     * Variant of {@link #findPrimaryUser()} that throws when no linked row exists. Use at
     * controller/service boundaries where an authenticated user is a hard precondition.
     */
    public User requireCurrentUser() {
        return findPrimaryUser().orElseThrow(() -> new EntityNotFoundException("User", "current authenticated user"));
    }

    /**
     * Return the linked row whose provider matches {@code desiredType}.
     * <p>
     * Useful when an action is inherently provider-scoped (e.g. creating a GitHub workspace
     * must use the GitHub row so subsequent GitHub API calls resolve) and we want to avoid
     * attaching state to the wrong IdP. Returns empty when that provider is not linked or
     * when claim resolution failed closed due to ambiguity.
     */
    public Optional<User> findLinkedUserForProvider(GitProviderType desiredType) {
        List<User> linked = findAllLinkedUsers();
        return linked
            .stream()
            .filter(u -> u.getProvider() != null && u.getProvider().getType() == desiredType)
            .findFirst();
    }
}
