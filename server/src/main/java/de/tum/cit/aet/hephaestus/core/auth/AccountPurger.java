package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-account hard-delete unit of work. Separate bean from {@link AccountHardDeleteSweeper} so the
 * {@code REQUIRES_NEW} boundary is a real proxy hop (a self-invocation would bypass it — the same
 * reason {@code AuthEventWriter} is split from {@code AuthEventLogger}). Each account purges in its own
 * transaction so one bad row never blocks the rest of the GDPR erasure backlog.
 */
@Component
@WorkspaceAgnostic("Account hard-delete is account-scoped; the sweep is global, not tenant data")
public class AccountPurger {

    private static final Logger log = LoggerFactory.getLogger(AccountPurger.class);

    /** PII-cleared placeholder left on the tombstone so the NOT NULL display_name column stays valid. */
    private static final String TOMBSTONE_DISPLAY_NAME = "deleted-account";

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;

    public AccountPurger(AccountRepository accountRepository, JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Purge one account in its OWN transaction. Throws on failure so the caller can isolate it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(Long accountId) {
        // Children carry ON DELETE CASCADE on account_id, but we keep the account tombstone, so the
        // cascade is not triggered — delete the personal/auth child rows explicitly.
        jdbcTemplate.update("DELETE FROM account_feature WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM identity_link WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM issued_jwt WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM account_export WHERE account_id = ?", accountId);
        anonymizeAuditRows(accountId);

        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return;
        }
        // Clear PII on the surviving tombstone and flip to the terminal state.
        account.setStatus(Account.Status.DELETED);
        account.setDisplayName(TOMBSTONE_DISPLAY_NAME);
        account.setPrimaryEmail(null);
        account.setPrimaryEmailVerifiedAt(null);
        accountRepository.save(account);
        log.info("auth.account: hard-deleted accountId={} (purged auth rows, status=DELETED)", accountId);
    }

    /**
     * GDPR Art. 17 erasure for the retained audit trail. The {@code auth_event} rows are kept under the
     * Art. 30 / Art. 17(3)(b) records-of-processing carve-out, but the personal data they carry has no
     * retention basis once the subject is erased: the raw {@code ip_inet}, the {@code user_agent}
     * fingerprint, and — for {@code IMPERSONATION_*} rows — the operator-supplied free-text {@code reason}
     * plus another account's id embedded in {@code details}. Anonymize those to {@code NULL} while
     * preserving the non-identifying skeleton ({@code event_type}, {@code result}, {@code occurred_at}) so
     * the proof-of-deletion event and the Art. 30 trail survive. Covers rows where the erased subject is
     * either the event's subject ({@code account_id}) OR the impersonator ({@code acting_account_id}).
     */
    private void anonymizeAuditRows(Long accountId) {
        int redacted = jdbcTemplate.update(
            "UPDATE auth_event SET ip_inet = NULL, user_agent = NULL, details = NULL " +
                "WHERE account_id = ? OR acting_account_id = ?",
            accountId,
            accountId
        );
        if (redacted > 0) {
            log.info("auth.account: anonymized {} auth_event row(s) for erased accountId={}", redacted, accountId);
        }
    }
}
