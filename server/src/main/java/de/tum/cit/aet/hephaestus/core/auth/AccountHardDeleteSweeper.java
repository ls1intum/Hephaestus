package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the GDPR Art. 17 hard-delete that {@code AccountService.softDelete} promises. An account
 * sits in {@link Account.Status#DELETING} during the {@link AuthProperties#deleteCooldown 48h}
 * recovery window; once {@code deleted_at} is older than that, this sweep removes the account's
 * personal / auth data and flips it to {@link Account.Status#DELETED}.
 *
 * <h2>What it purges</h2>
 * The account's child auth rows — {@code identity_link}, {@code account_feature}, {@code issued_jwt},
 * {@code account_export} — are removed via the {@code ON DELETE CASCADE} FK on each
 * {@code account_id} (see the auth-module changelog). We trigger that cascade
 * with a single {@code DELETE FROM account WHERE id = ?} would also drop the tombstone, so instead we
 * delete the children explicitly and keep a minimal {@code account} tombstone (status DELETED, PII
 * fields cleared). The tombstone preserves referential integrity for retained, lawful-basis rows
 * (e.g. {@code auth_event}, which is GDPR Art. 30 audit data with its own 12-month retention and no
 * FK to {@code account}) and makes the terminal DELETED state observable.
 *
 * <h2>ExternalActor / git mirror (Art. 17(3))</h2>
 * The git-provider activity mirror ({@code ExternalActor} in {@code gitprovider.actor.*}) is a
 * separate read-only entity recording authorship; it is referenced FROM {@code identity_link}
 * ({@code external_actor_id}), not the other way around, so deleting the identity links severs the
 * personal ↔ mirror association automatically. The activity graph itself is retained under the
 * Art. 17(3) public-archive carve-out and is out of scope for this sweep.
 *
 * <h2>Scheduling &amp; safety</h2>
 * Mirrors {@code ExportRetentionSweeper} / {@code AuthEventPartitionManager}: {@code @Scheduled} is
 * gated by {@code ServerSchedulingConfig} (server role only) and {@code @SchedulerLock} single-flights
 * across replicas. The work is idempotent — re-running after a flipped status is a no-op because the
 * row is no longer {@code DELETING}, and the child deletes are unconditional {@code DELETE ... WHERE
 * account_id = ?}.
 */
@Component
@WorkspaceAgnostic("Account hard-delete is account-scoped; the sweep is global, not tenant data")
public class AccountHardDeleteSweeper {

    private static final Logger log = LoggerFactory.getLogger(AccountHardDeleteSweeper.class);

    /** PII-cleared placeholder left on the tombstone so the NOT NULL display_name column stays valid. */
    private static final String TOMBSTONE_DISPLAY_NAME = "deleted-account";

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuthProperties properties;
    private final Clock clock;

    public AccountHardDeleteSweeper(
        AccountRepository accountRepository,
        JdbcTemplate jdbcTemplate,
        AuthProperties properties,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /** Runs hourly. {@code sweepNow()} is also callable directly from tests. */
    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(name = "account-hard-delete-sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void sweep() {
        int purged = sweepNow();
        if (purged > 0) {
            log.info("auth.account: hard-deleted {} account(s) past the GDPR delete cooldown", purged);
        }
    }

    /**
     * Hard-delete every account whose soft-delete cooldown has elapsed. Returns the number purged.
     * Idempotent: an already-DELETED account is never selected.
     */
    @Transactional
    public int sweepNow() {
        Instant cutoff = clock.instant().minus(properties.deleteCooldown());
        List<Long> ids = accountRepository.findDeletingPastCooldown(cutoff);
        for (Long id : ids) {
            purge(id);
        }
        return ids.size();
    }

    private void purge(Long accountId) {
        // Children carry ON DELETE CASCADE on account_id, but we keep the account tombstone, so the
        // cascade is not triggered — delete the personal/auth child rows explicitly. account_feature
        // also has enabled_by_account_id (ON DELETE SET NULL), cleared here for the deleted actor.
        jdbcTemplate.update(
            "UPDATE account_feature SET enabled_by_account_id = NULL WHERE enabled_by_account_id = ?",
            accountId
        );
        jdbcTemplate.update("DELETE FROM account_feature WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM identity_link WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM issued_jwt WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM account_export WHERE account_id = ?", accountId);

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
}
