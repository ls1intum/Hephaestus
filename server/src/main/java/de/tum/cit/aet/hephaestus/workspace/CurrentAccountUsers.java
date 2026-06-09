package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the SCM {@link User} mirror(s) for the CURRENT account — across every linked identity, not
 * just the one the session signed in with.
 *
 * <p>Workspace membership is keyed by the SCM {@code user}, but a single Hephaestus account (ADR 0017)
 * can link several provider identities (e.g. a GitLab login AND a GitHub login). The cookie-JWT only
 * carries one {@code preferred_username}, so {@code UserRepository.getCurrentUser()} resolves a single
 * SCM user — which means a member of a GitHub workspace who is currently signed in via GitLab would
 * resolve to their GitLab actor and see no workspaces. This resolver instead takes the JWT {@code sub}
 * (account id) → the account's active {@link AccountIdentityQuery.IdentityLinkView}s → the matching SCM
 * users, so membership/visibility unions across ALL of the account's identities.
 *
 * <p><strong>Provider-scoped resolution (never login-only).</strong> Each identity is resolved to its
 * SCM user by the already-wired actor id ({@code externalActorId}), or else by {@code (gitProviderId,
 * login)}. It must NEVER be resolved by login alone: {@code user} uniqueness is provider-scoped
 * ({@code uk_user_provider_login}), so the same login under a DIFFERENT provider belongs to a different
 * person — a login-only union would pull a stranger's workspace memberships into this account
 * (cross-provider horizontal privilege escalation).
 */
@Component
@WorkspaceAgnostic("Resolves the principal's SCM user mirrors; not scoped to a single workspace")
public class CurrentAccountUsers {

    private final AccountIdentityQuery accountIdentityQuery;
    private final UserRepository userRepository;

    public CurrentAccountUsers(AccountIdentityQuery accountIdentityQuery, UserRepository userRepository) {
        this.accountIdentityQuery = accountIdentityQuery;
        this.userRepository = userRepository;
    }

    /**
     * The SCM users mirrored by the current account's active identities, each resolved within its own
     * provider. Falls back to the single {@code preferred_username} user when the JWT carries no account
     * id (e.g. a legacy token), so behaviour is never worse than the previous single-identity resolution.
     */
    @Transactional(readOnly = true)
    public List<User> resolve() {
        List<AccountIdentityQuery.IdentityLinkView> links = SecurityUtils.getCurrentAccountId()
            .map(accountIdentityQuery::activeLinksForAccount)
            .orElseGet(List::of);
        if (links.isEmpty()) {
            return userRepository.getCurrentUser().map(List::of).orElseGet(List::of);
        }
        // Dedupe by user id (two links could wire to the same actor), preserving link order.
        Map<Long, User> byId = new LinkedHashMap<>();
        for (AccountIdentityQuery.IdentityLinkView link : links) {
            resolveLinkUser(link)
                .filter(user -> user.getId() != null)
                .ifPresent(user -> byId.putIfAbsent(user.getId(), user));
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Resolve a single identity to its SCM user, scoped to that identity's provider. Prefer the wired
     * {@code externalActorId} (the exact {@code User} row); otherwise match {@code (provider, login)}.
     */
    private Optional<User> resolveLinkUser(AccountIdentityQuery.IdentityLinkView link) {
        if (link.externalActorId() != null) {
            return userRepository.findById(link.externalActorId());
        }
        String login = link.usernameAtSignup();
        if (link.gitProviderId() == null || login == null || login.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByLoginAndProviderId(login, link.gitProviderId());
    }
}
