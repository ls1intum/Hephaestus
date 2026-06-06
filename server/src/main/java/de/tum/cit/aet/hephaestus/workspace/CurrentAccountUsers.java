package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the SCM {@link User} mirror(s) for the CURRENT account — across every linked identity, not
 * just the one the session signed in with.
 *
 * <p>Workspace membership is keyed by the SCM {@code user} (login bridge), but a single Hephaestus
 * account (ADR 0017) can link several provider identities (e.g. a GitLab login AND a GitHub login). The
 * cookie-JWT only carries one {@code preferred_username}, so {@code UserRepository.getCurrentUser()}
 * resolves a single SCM user — which means a member of a GitHub workspace who is currently signed in via
 * GitLab would resolve to their GitLab actor and see no workspaces. This resolver instead takes the JWT
 * {@code sub} (account id) → the account's active {@link AccountIdentityQuery.IdentityLinkView}s → their
 * provider logins → the matching SCM users, so membership/visibility unions across ALL of the account's
 * identities. It is strictly scoped to the authenticated account's own links, so it can never widen
 * access beyond what the account already owns.
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
     * The SCM users mirrored by the current account's active identities. Falls back to the single
     * {@code preferred_username} user when the JWT carries no account id (e.g. a legacy token), so
     * behaviour is never worse than the previous single-identity resolution.
     */
    @Transactional(readOnly = true)
    public List<User> resolve() {
        Set<String> logins = currentAccountLoginsLower();
        if (logins.isEmpty()) {
            return userRepository.getCurrentUser().map(List::of).orElseGet(List::of);
        }
        return userRepository.findAllByLoginLowerIn(logins);
    }

    /**
     * The lower-cased provider logins of the current account's active identities (the bridge key into the
     * SCM {@code user.login}). Empty when unauthenticated or the JWT has no account id.
     */
    @Transactional(readOnly = true)
    public Set<String> currentAccountLoginsLower() {
        return SecurityUtils.getCurrentAccountId()
            .map(accountIdentityQuery::activeLinksForAccount)
            .orElseGet(List::of)
            .stream()
            .map(AccountIdentityQuery.IdentityLinkView::usernameAtSignup)
            .filter(login -> login != null && !login.isBlank())
            .map(login -> login.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
