package de.tum.cit.aet.hephaestus.core.auth.jwt;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the {@link JwtPrincipal} (login + roles + given name) for an account at token-issue
 * time. Centralises the "what goes into the JWT claims" logic so the issuer
 * stays a thin signer and every issue path (login, refresh, impersonation) is consistent.
 *
 * <h2>Login resolution</h2>
 * The {@code preferred_username} must remain the git-provider login that the existing
 * authorization model keys on ({@code preferred_username → User.login → WorkspaceMembership}).
 * We take it from the account's most-recently-used active {@link IdentityLink}'s
 * {@code usernameAtSignup} — the same value the provider returns as the login.
 *
 * <h2>Role resolution</h2>
 * {@code app_admin} when the account is {@link Account.AppRole#APP_ADMIN}, plus every enabled
 * {@code account_feature} flag (e.g. {@code mentor_access}, {@code run_practice_review}). The
 * instance-admin authority is namespaced {@code app_admin} (matching the {@code /.well-known}
 * discovery doc and {@code SecurityUtils.isSuperAdmin}) — deliberately distinct from the
 * per-workspace "admin" role, which is membership-derived and never appears in this token.
 */
@Service
@WorkspaceAgnostic("JWT principal assembly is account-scoped")
public class JwtPrincipalFactory {

    /**
     * Authority strings that must never originate from a grantable {@code account_feature} flag — the
     * instance-admin authority comes only from {@link Account.AppRole#APP_ADMIN}. {@code admin} is
     * also reserved (it is the legacy pre-rename string and the per-workspace role name).
     */
    private static final Set<String> RESERVED_INSTANCE_AUTHORITIES = Set.of("app_admin", "admin");

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final AccountFeatureRepository accountFeatureRepository;

    public JwtPrincipalFactory(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        AccountFeatureRepository accountFeatureRepository
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.accountFeatureRepository = accountFeatureRepository;
    }

    @Transactional(readOnly = true)
    public JwtPrincipal forAccountId(Long accountId) {
        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account not found"));
        return forAccount(account);
    }

    @Transactional(readOnly = true)
    public JwtPrincipal forAccount(Account account) {
        // Defense-in-depth account-status gate (ADR 0017). Every JWT-issue path funnels through here
        // (login success handler, token refresh, impersonation). A SUSPENDED / DELETING / DELETED
        // account must never be minted a principal — even if a caller forgot the upstream check. The
        // OAuth success handler rejects earlier with a friendly redirect; this is the last line.
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account is not active");
        }
        String login = resolveLogin(account);
        Set<String> roles = new HashSet<>(accountFeatureRepository.findFlagsByAccountId(account.getId()));
        // Privilege separation: the instance-admin authority derives ONLY from Account.appRole, never
        // from a grantable account_feature row. Strip any feature flag that collides with a reserved
        // instance authority so a `/admin/users`-granted flag can't inject super-admin (account_feature
        // .flag is free-text, so this is the enforcement point).
        roles.removeAll(RESERVED_INSTANCE_AUTHORITIES);
        if (account.getAppRole() == Account.AppRole.APP_ADMIN) {
            roles.add("app_admin");
        }
        return new JwtPrincipal(account.getId(), login, account.getDisplayName(), roles);
    }

    /**
     * The git-provider login for the account: the username on its most recently used active
     * identity link, falling back to the first active link, then the display name. The login
     * is what {@code preferred_username} carries — it must match the synced {@code User.login}.
     */
    private String resolveLogin(Account account) {
        return identityLinkRepository
            .findActiveByAccountId(account.getId())
            .stream()
            .filter(il -> il.getUsernameAtSignup() != null && !il.getUsernameAtSignup().isBlank())
            .max(JwtPrincipalFactory::byLastLogin)
            .map(IdentityLink::getUsernameAtSignup)
            .orElse(account.getDisplayName());
    }

    private static int byLastLogin(IdentityLink a, IdentityLink b) {
        var la = a.getLastLoginAt();
        var lb = b.getLastLoginAt();
        if (la == null && lb == null) return 0;
        if (la == null) return -1;
        if (lb == null) return 1;
        return la.compareTo(lb);
    }
}
