package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountWorkspaceMembershipQuery;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-account hard-delete unit of work. Separate bean from {@link AccountHardDeleteSweeper} so the
 * {@code REQUIRES_NEW} boundary is a real proxy hop (a self-invocation would bypass it — the same
 * reason {@code AuthEventWriter} is split from {@code AuthEventLogger}). Each account purges in its own
 * transaction so one bad row never blocks the rest of the GDPR erasure backlog.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Account hard-delete is account-scoped; the sweep is global, not tenant data")
public class AccountPurger {

    private static final Logger log = LoggerFactory.getLogger(AccountPurger.class);

    /** PII-cleared placeholder left on the tombstone so the NOT NULL display_name column stays valid. */
    private static final String TOMBSTONE_DISPLAY_NAME = "deleted-account";

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final AccountWorkspaceMembershipQuery workspaceMembershipQuery;

    public AccountPurger(
        AccountRepository accountRepository,
        JdbcTemplate jdbcTemplate,
        NamedParameterJdbcTemplate namedJdbcTemplate,
        AccountWorkspaceMembershipQuery workspaceMembershipQuery
    ) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.workspaceMembershipQuery = workspaceMembershipQuery;
    }

    /** Purge one account in its OWN transaction. Throws on failure so the caller can isolate it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(Long accountId) {
        // Resolve the SCM actors BEFORE identity_link is deleted below — that table is the only edge from the
        // account to the actor ids the disclosure trail is keyed on. Both sources are needed:
        // external_actor_id is a best-effort bind that is often NULL, so the membership hop off the signup
        // login is what covers those accounts (the GDPR export unions the same two).
        Set<Long> scmActorIds = resolveScmActorIds(accountId);
        // Children carry ON DELETE CASCADE on account_id, but we keep the account tombstone, so the
        // cascade is not triggered — delete the personal/auth child rows explicitly.
        jdbcTemplate.update("DELETE FROM account_feature WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM identity_link WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM issued_jwt WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM account_export WHERE account_id = ?", accountId);
        anonymizeAuditRows(accountId);
        anonymizeDataAccessRows(accountId, scmActorIds);

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
     * GDPR Art. 17 erasure for the retained audit trails. The {@code auth_event} rows are kept under the
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

        // config_audit_event carries no free-text or network identifiers, so only the account references
        // need clearing. The append-only trigger permits exactly this per-column nulling; the change
        // itself stays, which is what the Art. 17(3)(b) basis retains.
        int unlinked = jdbcTemplate.update(
            "UPDATE config_audit_event SET actor_account_id = NULL, acting_account_id = NULL " +
                "WHERE actor_account_id = ? OR acting_account_id = ?",
            accountId,
            accountId
        );
        if (unlinked > 0) {
            log.info(
                "auth.account: unlinked {} config_audit_event row(s) for erased accountId={}",
                unlinked,
                accountId
            );
        }
    }

    /**
     * The SCM actors this account resolves to: the eagerly-bound {@code external_actor_id} where one exists,
     * plus the actors its workspace memberships hang off. The bind is best-effort and frequently NULL, so the
     * membership hop is not redundant — without it an erased account keeps its name on the disclosure trail.
     */
    private Set<Long> resolveScmActorIds(Long accountId) {
        Set<Long> actorIds = new LinkedHashSet<>(
            jdbcTemplate.queryForList(
                "SELECT external_actor_id FROM identity_link WHERE account_id = ? AND external_actor_id IS NOT NULL",
                Long.class,
                accountId
            )
        );
        Set<String> logins = Set.copyOf(
            jdbcTemplate.queryForList(
                "SELECT username_at_signup FROM identity_link WHERE account_id = ? AND username_at_signup IS NOT NULL",
                String.class,
                accountId
            )
        );
        workspaceMembershipQuery
            .membershipsForLogins(logins)
            .stream()
            .map(AccountWorkspaceMembershipQuery.WorkspaceMembershipView::memberId)
            .filter(Objects::nonNull)
            .forEach(actorIds::add);
        return actorIds;
    }

    /**
     * The same Art. 17 unlink for the disclosure trail, which is keyed on the SCM actor rather than the
     * account. Both roles are cleared: the erased person may appear as the viewer or as the person whose
     * report was viewed. The rows stay — that a disclosure happened is the Art. 30 record, who it was about
     * is the personal data, and only that is removed.
     */
    private void anonymizeDataAccessRows(Long accountId, Set<Long> scmActorIds) {
        if (scmActorIds.isEmpty()) {
            return;
        }
        int unlinked = namedJdbcTemplate.update(
            """
            UPDATE data_access_event
               SET actor_user_id   = CASE WHEN actor_user_id   IN (:ids) THEN NULL ELSE actor_user_id   END,
                   subject_user_id = CASE WHEN subject_user_id IN (:ids) THEN NULL ELSE subject_user_id END
             WHERE actor_user_id IN (:ids) OR subject_user_id IN (:ids)
            """,
            Map.of("ids", scmActorIds)
        );
        if (unlinked > 0) {
            log.info("auth.account: unlinked {} data_access_event row(s) for erased accountId={}", unlinked, accountId);
        }
    }
}
