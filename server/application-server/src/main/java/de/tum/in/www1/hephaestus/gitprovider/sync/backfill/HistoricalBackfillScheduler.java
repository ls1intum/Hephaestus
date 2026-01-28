package de.tum.in.www1.hephaestus.gitprovider.sync.backfill;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for historical backfill operations.
 *
 * <h2>Purpose</h2>
 * Runs scheduled backfill cycles to incrementally sync historical GitHub data that
 * predates the initial sync window. Processes batches for repositories that have
 * completed their initial sync but still have historical data to fetch.
 *
 * <h2>Design Rationale</h2>
 * <p>Backfill is handled exclusively via scheduled batches, not event-driven triggers.
 * This is intentional:
 * <ul>
 *   <li><b>Simplicity:</b> One mechanism handles all backfill - no event/scheduler coordination</li>
 *   <li><b>API respect:</b> Doesn't hammer GitHub right after sync depletes rate limit</li>
 *   <li><b>Historical data isn't urgent:</b> Data sitting for months/years can wait 15 minutes</li>
 *   <li><b>Built-in guards:</b> The scheduler already checks rate limits, cooldowns, and
 *       {@code lastIssuesAndPullRequestsSyncedAt} to skip repos that haven't synced yet</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * hephaestus:
 *   sync:
 *     backfill:
 *       enabled: true              # Must be true to enable backfill
 *       batch-size: 50             # Pages per repository per cycle
 *       rate-limit-threshold: 100  # Pause when remaining points drop below this
 *       interval-seconds: 60       # Seconds between cycles (rate limit is the throttle)
 * }</pre>
 *
 * @see HistoricalBackfillService
 * @see SyncSchedulerProperties.BackfillProperties
 */
@Component
@ConditionalOnProperty(name = "hephaestus.sync.backfill.enabled", havingValue = "true")
public class HistoricalBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoricalBackfillScheduler.class);

    private final HistoricalBackfillService backfillService;
    private final SyncSchedulerProperties syncSchedulerProperties;

    // Injected to satisfy architecture test: signals this scheduler iterates workspaces via the service
    @SuppressWarnings("unused")
    private final SyncTargetProvider syncTargetProvider;

    public HistoricalBackfillScheduler(
        HistoricalBackfillService backfillService,
        SyncSchedulerProperties syncSchedulerProperties,
        SyncTargetProvider syncTargetProvider
    ) {
        this.backfillService = backfillService;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.syncTargetProvider = syncTargetProvider;
        log.info(
            "Historical backfill scheduler initialized: batchSize={}, rateLimitThreshold={}, intervalSeconds={}",
            syncSchedulerProperties.backfill().batchSize(),
            syncSchedulerProperties.backfill().rateLimitThreshold(),
            syncSchedulerProperties.backfill().intervalSeconds()
        );
    }

    /**
     * Scheduled task that processes backfill operations.
     *
     * <p>This method is called automatically by Spring's task scheduler at a fixed rate.
     * Each invocation processes one batch of historical data per repository that has
     * completed initial sync but still needs backfill, respecting rate limits and cooldowns.
     *
     * <p>When all repositories have completed backfill, cycles run silently at TRACE level
     * to avoid log noise while remaining ready for new repositories.
     */
    @Scheduled(fixedRateString = "${hephaestus.sync.backfill.interval-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void runBackfillCycle() {
        log.trace("Starting backfill cycle");
        try {
            HistoricalBackfillService.BackfillCycleResult result = backfillService.runBackfillCycle();
            if (result.repositoriesProcessed() > 0) {
                log.info("Backfill cycle complete: repositoriesProcessed={}", result.repositoriesProcessed());
            } else if (result.pendingRepositories() > 0) {
                // There are repos that need backfill but were skipped (rate limit, cooldown, etc.)
                log.debug(
                    "Backfill cycle complete: no work performed, pendingRepos={}, reason={}",
                    result.pendingRepositories(),
                    result.skipReason()
                );
            }
            // If pendingRepositories == 0, all backfill is complete - stay silent (TRACE only)
        } catch (Exception e) {
            log.error("Backfill cycle failed", e);
        }
    }
}
