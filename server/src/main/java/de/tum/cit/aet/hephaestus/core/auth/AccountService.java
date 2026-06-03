package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account read + lifecycle operations behind the {@code /user} and {@code /admin/users}
 * controllers. Owns the data-layer access so the controllers stay thin and never touch a
 * repository directly (enforced by {@code ArchitectureTest.controllersDoNotAccessRepositories}).
 */
@Service
@WorkspaceAgnostic("Account operations are user/system-scoped, not workspace-scoped")
public class AccountService {

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final IssuedJwtRepository issuedJwtRepository;
    private final AuthEventLogger authEventLogger;
    private final Clock clock;

    public AccountService(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        IssuedJwtRepository issuedJwtRepository,
        AuthEventLogger authEventLogger,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.issuedJwtRepository = issuedJwtRepository;
        this.authEventLogger = authEventLogger;
        this.clock = clock;
    }

    public Account requireById(Long id) {
        return accountRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
    }

    public List<IdentityLink> activeIdentities(Long accountId) {
        return identityLinkRepository.findActiveByAccountId(accountId);
    }

    /**
     * GDPR Art. 17 soft-delete: status → DELETING + deleted_at (start of the cooldown window), and
     * revoke all sessions immediately. Once {@code deleted_at} is older than
     * {@code hephaestus.auth.delete-cooldown} (default 48h), {@link AccountHardDeleteSweeper} purges
     * the account's personal/auth child rows (identity_link, account_feature, issued_jwt,
     * account_export) and flips the row to DELETED. Retained, lawful-basis audit data (auth_event,
     * Art. 30) and the read-only git-activity mirror (Art. 17(3)) are intentionally kept.
     */
    @Transactional
    public void softDelete(Long accountId) {
        Account account = requireById(accountId);
        account.setStatus(Account.Status.DELETING);
        account.setDeletedAt(clock.instant());
        accountRepository.save(account);
        issuedJwtRepository.revokeAllForAccount(accountId, clock.instant(), IssuedJwt.RevokedReason.ACCOUNT_DELETED);
        authEventLogger
            .event(AuthEvent.EventType.ACCOUNT_DELETED, AuthEvent.Result.SUCCESS)
            .account(accountId)
            .record();
    }

    /**
     * Unlink (remove) a federated identity from the current account. Two safety rules, both
     * standard for account-linking UIs:
     * <ul>
     *   <li><b>Ownership</b> — only a link the account actually owns can be removed (404 otherwise),
     *       so one user can never detach another's identity.</li>
     *   <li><b>Last-identity lockout</b> — the account's only remaining sign-in method cannot be
     *       removed (409). "The more ways a user can verify their identity, the less likely they lose
     *       access" (Auth0); to drop the last identity a user deletes the account instead.</li>
     * </ul>
     * Reversible: re-linking only requires signing in with that provider again. The current session
     * is account-scoped (not identity-scoped), so unlinking never logs the user out.
     */
    @Transactional
    public void unlinkIdentity(Long accountId, Long identityLinkId) {
        // Write-lock the account's active links so two concurrent unlinks of different identities
        // serialize — otherwise both pass the last-identity guard below and drain the account to zero.
        List<IdentityLink> active = identityLinkRepository.findActiveByAccountIdForUpdate(accountId);
        IdentityLink target = active
            .stream()
            .filter(il -> il.getId().equals(identityLinkId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "identity link not found"));
        if (active.size() <= 1) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "You can't unlink your only sign-in method. Link another provider first, or delete your account."
            );
        }
        Long gitProviderId = target.getGitProviderId();
        if (identityLinkRepository.deleteByIdAndAccountId(identityLinkId, accountId) == 0) {
            // Lost a race (concurrently removed) — nothing to do; surface as not-found.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "identity link not found");
        }
        // The link row is now gone, so don't reference its id in the audit (its auth_event FK is
        // ON DELETE SET NULL anyway); account + provider record who unlinked which provider.
        authEventLogger
            .event(AuthEvent.EventType.IDENTITY_UNLINKED, AuthEvent.Result.SUCCESS)
            .account(accountId)
            .gitProvider(gitProviderId)
            .record();
    }

    public List<Account> adminList(int page, int size) {
        int capped = Math.min(Math.max(size, 1), 200);
        return accountRepository.findAll(PageRequest.of(Math.max(page, 0), capped)).getContent();
    }

    @Transactional
    public Account adminSetRole(Long accountId, @Nullable String appRole, Long actingAccountId) {
        Account account = requireById(accountId);
        if (appRole != null) {
            Account.AppRole role;
            try {
                role = Account.AppRole.valueOf(appRole);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "unknown app role: " + appRole);
            }
            account.setAppRole(role);
            accountRepository.save(account);
            authEventLogger
                .event(AuthEvent.EventType.FEATURE_FLAG_CHANGED, AuthEvent.Result.SUCCESS)
                .account(accountId)
                .actingAccount(actingAccountId)
                .details("{\"appRole\":\"" + role.name() + "\"}")
                .record();
        }
        return account;
    }
}
