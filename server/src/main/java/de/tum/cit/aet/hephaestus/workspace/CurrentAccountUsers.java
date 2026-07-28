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
import java.util.Set;
import java.util.stream.Collectors;
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
    private final WorkspaceMembershipRepository membershipRepository;

    public CurrentAccountUsers(
        AccountIdentityQuery accountIdentityQuery,
        UserRepository userRepository,
        WorkspaceMembershipRepository membershipRepository
    ) {
        this.accountIdentityQuery = accountIdentityQuery;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
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
     * The current account's SCM user that holds membership in {@code workspaceId} — the identity this
     * workspace knows the caller as.
     *
     * <p>This, not {@code UserRepository.getCurrentUser()}, is what a surface serving "my own data" must
     * resolve the subject with. {@code getCurrentUser()} answers from the request's pinned login, which the
     * context filter sets only when the account is a member; on a publicly-readable workspace, or for an
     * elevated instance admin who is not a member, it falls back to the JWT's {@code preferred_username} and
     * resolves by login across providers. {@code user} uniqueness is provider-scoped, so the same login on
     * another provider is a different person — and serving them a report would be a cross-provider
     * disclosure, the exact hazard {@link #resolve()} exists to close.
     *
     * <p>Empty means "this account is not a member here", which callers must treat as a refusal, never as
     * "no data".
     */
    @Transactional(readOnly = true)
    public Optional<User> resolveMemberOf(Long workspaceId) {
        List<User> users = resolve();
        if (users.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> memberUserIds = membershipRepository
            .findByWorkspace_IdAndUser_IdIn(workspaceId, users.stream().map(User::getId).toList())
            .stream()
            .map(membership -> membership.getUser().getId())
            .collect(Collectors.toSet());
        return users
            .stream()
            .filter(user -> memberUserIds.contains(user.getId()))
            .findFirst();
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
