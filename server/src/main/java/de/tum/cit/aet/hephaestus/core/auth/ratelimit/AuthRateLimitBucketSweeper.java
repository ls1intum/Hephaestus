package de.tum.cit.aet.hephaestus.core.auth.ratelimit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.github.bucket4j.distributed.proxy.ExpiredEntriesCleaner;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Hourly sweep that prunes expired rows from {@code auth_rate_limit_bucket}, keeping the table
 * bounded. The buckets carry an {@code expires_at} populated by the {@code expirationAfterWrite}
 * strategy on the Postgres {@link ProxyManager} (see {@link AuthRateLimitConfig}); Bucket4j's SQL
 * proxy manager does not auto-delete, so without this sweep every distinct/rotating pre-auth client
 * IP would leave a permanent row.
 *
 * <p>Scheduling is gated by {@code ServerSchedulingConfig} ({@code @EnableScheduling} only on the
 * server role) and wrapped in {@link SchedulerLock} so concurrent server pods don't both sweep —
 * the same pattern as {@code ExportRetentionSweeper}.
 */
@WorkspaceAgnostic("Pruning global, workspace-agnostic auth rate-limit buckets")
public class AuthRateLimitBucketSweeper {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitBucketSweeper.class);

    /** Rows removed per batch; loop until a short batch so a backlog drains without one huge statement. */
    private static final int BATCH = 1_000;

    /** Backstop so a pathological state can never spin forever (1000 batches = 1M rows). */
    private static final int MAX_BATCHES = 1_000;

    private final ExpiredEntriesCleaner cleaner;

    public AuthRateLimitBucketSweeper(ProxyManager<String> proxyManager) {
        // The Postgres SELECT-FOR-UPDATE proxy manager implements ExpiredEntriesCleaner.
        this.cleaner = (ExpiredEntriesCleaner) proxyManager;
    }

    @Scheduled(cron = "0 30 * * * *")
    @SchedulerLock(name = "auth-rate-limit-bucket-sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void sweep() {
        int total = sweepNow();
        if (total > 0) {
            log.info("auth.ratelimit: pruned {} expired rate-limit bucket(s)", total);
        }
    }

    /** Removes expired buckets in batches; returns the total removed. Also callable directly from tests. */
    public int sweepNow() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int removed = cleaner.removeExpired(BATCH);
            total += removed;
            if (removed < BATCH) {
                break;
            }
        }
        return total;
    }
}
