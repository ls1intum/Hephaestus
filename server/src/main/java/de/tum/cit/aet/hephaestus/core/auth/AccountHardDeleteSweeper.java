package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces the GDPR Art. 17 hard-delete that {@code AccountService.softDelete} promises. An account
 * sits in {@link Account.Status#DELETING} during the {@link AuthProperties#deleteCooldown 48h}
 * grace period (a delayed purge — the user cannot self-recover; sessions are revoked immediately on
 * soft-delete); once {@code deleted_at} is older than that, this sweep removes the account's
 * personal / auth data and flips it to {@link Account.Status#DELETED}.
 *
 * <h2>What it purges</h2>
 * The account's child auth rows — {@code identity_link}, {@code account_feature}, {@code issued_jwt},
 * {@code account_export} — carry an {@code ON DELETE CASCADE} FK on {@code account_id}, but because we
 * keep a minimal {@code account} tombstone (status DELETED, PII cleared) rather than deleting the row,
 * that cascade never fires. So the children are deleted explicitly. The tombstone preserves referential
 * integrity for retained, lawful-basis rows (e.g. {@code auth_event}, GDPR Art. 30 audit data with its
 * own 12-month retention) and makes the terminal DELETED state observable.
 *
 * <h2>Git activity mirror (Art. 17(3))</h2>
 * The git-provider activity mirror ({@code integration.scm.domain.user.User}) is a separate read-only
 * entity recording authorship; it is referenced FROM {@code identity_link} ({@code external_actor_id}),
 * not the other way around, so deleting the identity links severs the personal ↔ mirror association
 * automatically. The activity graph itself is retained under the Art. 17(3) public-archive carve-out
 * and is out of scope for this sweep.
 *
 * <h2>Scheduling &amp; safety</h2>
 * Mirrors {@code ExportRetentionSweeper} / {@code AuthEventPartitionMaintenance}: {@code @Scheduled} is
 * gated by {@code ServerSchedulingConfig} (server role only) and {@code @SchedulerLock} single-flights
 * across replicas. The work is idempotent — re-running after a flipped status is a no-op because the
 * row is no longer {@code DELETING}, and the child deletes are unconditional {@code DELETE ... WHERE
 * account_id = ?}.
 */
@Component
@WorkspaceAgnostic("Account hard-delete is account-scoped; the sweep is global, not tenant data")
public class AccountHardDeleteSweeper {

    private static final Logger log = LoggerFactory.getLogger(AccountHardDeleteSweeper.class);

    /** Page size for the erasure backlog — one bounded transaction set per account, not one giant one. */
    private static final int PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final AccountPurger accountPurger;
    private final AuthProperties properties;
    private final Clock clock;

    public AccountHardDeleteSweeper(
        AccountRepository accountRepository,
        AccountPurger accountPurger,
        AuthProperties properties,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.accountPurger = accountPurger;
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
     * Hard-delete every account whose soft-delete cooldown has elapsed. Orchestrator only — each account
     * is purged in its OWN transaction via {@link AccountPurger} so one bad row never blocks the rest of
     * the GDPR erasure backlog. Returns the count of SUCCESSFUL purges; a failed account stays DELETING
     * and is retried next sweep. Idempotent: an already-DELETED account is never selected.
     */
    public int sweepNow() {
        Instant cutoff = clock.instant().minus(properties.deleteCooldown());
        int total = 0;
        // Always fetch page 0: a successful purge flips the account to DELETED so it drops out of the
        // result set, advancing the window naturally. We stop when a page is empty (backlog drained) or
        // when a page made ZERO progress (every purge threw) — otherwise the same failing rows would be
        // re-fetched forever. Failed accounts stay DELETING and are retried on the next sweep.
        while (true) {
            List<Long> page = accountRepository.findDeletingPastCooldown(cutoff, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            int purgedThisPage = 0;
            for (Long id : page) {
                try {
                    accountPurger.purge(id);
                    total++;
                    purgedThisPage++;
                } catch (RuntimeException e) {
                    log.error(
                        "auth.account: hard-delete FAILED for accountId={} (skipped, will retry next sweep)",
                        id,
                        e
                    );
                }
            }
            if (purgedThisPage == 0) {
                break;
            }
        }
        return total;
    }
}
