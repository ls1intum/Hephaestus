package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
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
 *       user-attributed writes (e.g. workspace ownership, activity logs). Chosen as the row
 *       whose login matches {@code preferred_username}; ties broken by lowest {@code id} for
 *       determinism.</li>
 * </ul>
 * Lookups are driven exclusively by the authoritative {@code github_id} / {@code gitlab_id}
 * JWT claims (populated by Keycloak IdP protocol mappers). There is intentionally no
 * {@code preferred_username} fallback: that claim is operator-settable and matching on it
 * across providers would let a misconfigured token resolve to an unrelated user row.
 */
@Service
@Transactional(readOnly = true)
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Every {@link User} row linked to the current authenticated principal. Empty when no
     * identity claim is present (unauthenticated request, or a token issued before the
     * Keycloak IdP mappers were deployed).
     */
    public List<User> findAllLinkedUsers() {
        var byId = new LinkedHashMap<Long, User>();

        SecurityUtils.getCurrentGitHubId()
            .map(id -> userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITHUB, id))
            .ifPresent(users -> users.forEach(u -> byId.putIfAbsent(u.getId(), u)));

        SecurityUtils.getCurrentGitLabId()
            .map(id -> userRepository.findAllByProviderTypeAndNativeId(GitProviderType.GITLAB, id))
            .ifPresent(users -> users.forEach(u -> byId.putIfAbsent(u.getId(), u)));

        return new ArrayList<>(byId.values());
    }

    /**
     * The {@link User} row that best represents the current principal for single-user APIs.
     * Prefers the linked row whose login matches {@code preferred_username}; falls back to
     * the row with the lowest {@code id} for deterministic selection.
     */
    public Optional<User> findPrimaryUser() {
        List<User> linked = findAllLinkedUsers();
        if (linked.isEmpty()) {
            return Optional.empty();
        }
        if (linked.size() == 1) {
            return Optional.of(linked.get(0));
        }
        Optional<String> preferredLogin = SecurityUtils.getCurrentUserLogin();
        if (preferredLogin.isPresent()) {
            String login = preferredLogin.get();
            var match = linked
                .stream()
                .filter(u -> login.equalsIgnoreCase(u.getLogin()))
                .findFirst();
            if (match.isPresent()) {
                return match;
            }
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
     * Prefer the linked row whose provider matches {@code desiredType}; fall back to the
     * {@linkplain #findPrimaryUser() primary} row if no linked row uses that provider.
     * <p>
     * Useful when an action is inherently provider-scoped (e.g. creating a GitHub workspace
     * must use the GitHub row so subsequent GitHub API calls resolve) and we want to avoid
     * attaching state to the wrong IdP.
     */
    public Optional<User> findLinkedUserForProvider(GitProviderType desiredType) {
        List<User> linked = findAllLinkedUsers();
        return linked
            .stream()
            .filter(u -> u.getProvider() != null && u.getProvider().getType() == desiredType)
            .findFirst()
            .or(this::findPrimaryUser);
    }
}
