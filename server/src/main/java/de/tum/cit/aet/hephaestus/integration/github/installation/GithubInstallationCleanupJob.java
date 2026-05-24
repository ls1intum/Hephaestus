package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.github.app.GitHubAppTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweeper for expired {@link GithubInstallationUnbound} rows.
 *
 * <p>Opt-in via {@code hephaestus.integration.github.unbound-cleanup.enabled=true}. When
 * enabled, the job runs daily at 03:30 server time and on each pass:
 *
 * <ol>
 *   <li>Selects up to {@link #BATCH_LIMIT} expired rows.</li>
 *   <li>For each row, calls {@code DELETE /app/installations/{id}} on GitHub to evict
 *       the install upstream (gated by {@code ...unbound-cleanup.delete-from-github}).</li>
 *   <li>Only on GitHub-DELETE success (or 404 — already gone) deletes the local row.</li>
 * </ol>
 *
 * <p><b>Multi-pod safety.</b> Wrapped in {@link SchedulerLock @SchedulerLock} so only
 * one pod runs the cleanup at any moment. {@code lockAtMostFor=PT30M} bounds the lock
 * even if the holding pod crashes mid-pass; {@code lockAtLeastFor=PT1M} prevents
 * thrashing if two pods' clocks drift past the cron boundary within the same second.
 *
 * <p><b>Why the GitHub DELETE matters.</b> Without it, expired parking rows are dropped
 * locally but the App installation lingers on GitHub — wasting the vendor-side
 * install quota and leaving a webhook target that we no longer accept. The operator
 * can disable just the GitHub-side delete (keeping local cleanup) via
 * {@code hephaestus.integration.github.unbound-cleanup.delete-from-github=false} if
 * the vendor API is being investigated.
 *
 * <p>Per-pass batch size is capped at 50 to keep one pass cheap and predictable.
 */
@Component
@WorkspaceAgnostic("Operates on global pre-workspace bootstrap table")
@ConditionalOnProperty(name = "hephaestus.integration.github.unbound-cleanup.enabled", havingValue = "true")
public class GithubInstallationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationCleanupJob.class);

    static final int BATCH_LIMIT = 50;

    private final GithubInstallationUnboundRepository repository;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final boolean deleteFromGithub;
    private final Counter cleanedCounter;
    private final Counter githubDeleteFailedCounter;

    public GithubInstallationCleanupJob(
        GithubInstallationUnboundRepository repository,
        GitHubAppTokenService gitHubAppTokenService,
        @Value("${hephaestus.integration.github.unbound-cleanup.delete-from-github:true}") boolean deleteFromGithub,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.deleteFromGithub = deleteFromGithub;
        this.cleanedCounter = Counter.builder("github.installation.unbound.cleaned")
            .description("Number of expired GitHub App installation parking rows pruned")
            .register(meterRegistry);
        this.githubDeleteFailedCounter = Counter.builder("github.installation.unbound.github_delete_failed")
            .description("GitHub-side DELETE /app/installations/{id} failures (retries next pass)")
            .register(meterRegistry);
    }

    /**
     * Runs daily at 03:30 server time. ShedLock guarantees single-writer across the
     * whole cluster — {@code lockAtMostFor=PT30M} is a safety net for crashed pods,
     * {@code lockAtLeastFor=PT1M} avoids the cron-edge thrash.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(
        name = "github-installation-unbound-cleanup",
        lockAtMostFor = "PT30M",
        lockAtLeastFor = "PT1M"
    )
    @Transactional
    public void cleanupExpired() {
        List<GithubInstallationUnbound> expired = repository.findByExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) {
            log.info("GithubInstallationCleanupJob: no expired unbound installations");
            return;
        }

        int processed = 0;
        int localDeleteFailed = 0;
        int githubDeleteFailed = 0;
        for (GithubInstallationUnbound row : expired) {
            if (processed >= BATCH_LIMIT) {
                log.info(
                    "GithubInstallationCleanupJob: hit BATCH_LIMIT={} with {} rows still pending; will resume next pass",
                    BATCH_LIMIT, expired.size() - processed
                );
                break;
            }
            long installationId = row.getInstallationId();

            if (deleteFromGithub) {
                try {
                    gitHubAppTokenService.deleteInstallation(installationId);
                } catch (RuntimeException e) {
                    // GitHub-side delete failed — DO NOT drop the local row, otherwise we
                    // orphan the installation upstream. Next pass will retry.
                    githubDeleteFailed++;
                    githubDeleteFailedCounter.increment();
                    log.warn(
                        "GithubInstallationCleanupJob: GitHub DELETE /app/installations/{} failed ({}); skipping local delete, retrying next pass",
                        installationId, e.getMessage()
                    );
                    continue;
                }
            }

            try {
                repository.delete(row);
                cleanedCounter.increment();
                processed++;
            } catch (RuntimeException e) {
                // Local delete failed AFTER GitHub-side already deleted: log loud — the
                // GitHub install is gone but our parking row remains. Next pass will
                // observe findByExpiresAtBefore() pick it up, GitHub will return 404
                // (treated as success), and the second local delete attempt will run.
                localDeleteFailed++;
                log.warn(
                    "GithubInstallationCleanupJob: local delete failed for installation={} ({}), continuing — next pass will retry",
                    installationId, e.getMessage()
                );
            }
        }
        log.info(
            "GithubInstallationCleanupJob: cleaned {} expired unbound rows ({} local-delete failures, {} github-delete failures, deleteFromGithub={})",
            processed, localDeleteFailed, githubDeleteFailed, deleteFromGithub
        );
    }
}
