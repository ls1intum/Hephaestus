package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweeper for expired {@link GithubInstallationUnbound} rows.
 *
 * <p>Opt-in via {@code hephaestus.integration.github.unbound-cleanup.enabled=true}. Plan
 * v4 D17 wants the GitHub-side {@code DELETE /app/installations/{id}} call to be gated
 * behind explicit operator action until we have confidence — the current implementation
 * is the LOCAL half (delete the parking row) only.
 *
 * <p><b>ShedLock not on classpath.</b> A grep of {@code server/pom.xml} for
 * {@code shedlock} returned no hits, so the multi-pod lock guard requested by the
 * deliverable cannot be wired without a new dependency. As a stopgap we run unguarded
 * and log a WARN on every pass so operators see the gap. The lock is mandatory before
 * scale-out — TODO(#1198) below.
 *
 * <p>Per-pass batch size is capped at 50 to keep one pass cheap and predictable.
 */
@Component
@WorkspaceAgnostic("Operates on global pre-workspace bootstrap table")
@ConditionalOnProperty(name = "hephaestus.integration.github.unbound-cleanup.enabled", havingValue = "true")
public class GithubInstallationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationCleanupJob.class);

    private static final int BATCH_LIMIT = 50;

    private final GithubInstallationUnboundRepository repository;
    private final Counter cleanedCounter;

    public GithubInstallationCleanupJob(GithubInstallationUnboundRepository repository,
                                        MeterRegistry meterRegistry) {
        this.repository = repository;
        this.cleanedCounter = Counter.builder("github.installation.unbound.cleaned")
            .description("Number of expired GitHub App installation parking rows pruned")
            .register(meterRegistry);
    }

    /**
     * Runs daily at 03:30 server time.
     *
     * <p>TODO(#1198): Once ShedLock is on the classpath, wrap with
     * {@code @SchedulerLock(name = "github-installation-unbound-cleanup",
     * lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")} so multi-pod replicas
     * don't double-process.
     *
     * <p>TODO(#1198): Call GitHub REST {@code DELETE /app/installations/{installation_id}}
     * to evict the installation upstream. The auth is a GitHub App JWT minted via
     * {@code GitHubAppTokenService} (RS256 with the App private key, {@code iat}/{@code exp}
     * within 10 min, {@code iss} = app id). Requires {@code Accept: application/vnd.github+json}.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupExpired() {
        log.warn(
            "GithubInstallationCleanupJob running WITHOUT distributed lock — ShedLock not on classpath. "
                + "Multi-pod deployments may double-process. Add a ShedLock dependency before scale-out."
        );

        List<GithubInstallationUnbound> expired = repository.findByExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) {
            log.info("GithubInstallationCleanupJob: no expired unbound installations");
            return;
        }

        int processed = 0;
        int failed = 0;
        for (GithubInstallationUnbound row : expired) {
            if (processed >= BATCH_LIMIT) {
                log.info(
                    "GithubInstallationCleanupJob: hit BATCH_LIMIT={} with {} rows still pending; will resume next pass",
                    BATCH_LIMIT, expired.size() - processed
                );
                break;
            }
            try {
                repository.delete(row);
                cleanedCounter.increment();
                processed++;
            } catch (RuntimeException e) {
                // One bad row should NOT abort the whole batch. The next pass picks it up;
                // a stuck row will surface in metrics as a persistent gap between
                // findByExpiresAtBefore() size and the cleaned counter.
                failed++;
                log.warn(
                    "GithubInstallationCleanupJob: failed to delete installation={} ({}), continuing",
                    row.getInstallationId(), e.getMessage()
                );
            }
        }
        log.info("GithubInstallationCleanupJob: cleaned {} expired unbound rows ({} failed)", processed, failed);
    }
}
