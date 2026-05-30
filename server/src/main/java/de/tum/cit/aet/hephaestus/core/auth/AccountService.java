package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
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
