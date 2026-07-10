package de.tum.cit.aet.hephaestus.integration.core.oauth.state;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweeper for {@link OAuthStateNonce} rows older than the configured retention
 * window. With a 10-minute HMAC TTL, anything past a few hours is guaranteed
 * unrecoverable — keeping the table empty bounds storage and keeps the
 * {@link OAuthStateNonceRepository#markConsumed} hot path cheap.
 *
 * <p><b>Multi-pod safety.</b> Wrapped in {@link SchedulerLock @SchedulerLock} so
 * concurrent server pods don't both race the DELETE. {@code lockAtMostFor=PT10M}
 * is generous for what amounts to one indexed range delete.
 *
 * <p>Retention defaults to 7 days via
 * {@code hephaestus.integration.oauth-state.nonce-retention} — anything longer
 * means a vendor callback that took DAYS to arrive, which is well past every
 * sensible TTL. Shorten in tests via the property.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Pruning a workspace-agnostic table")
public class OAuthStateNonceCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OAuthStateNonceCleanupJob.class);

    private final OAuthStateNonceRepository repository;
    private final Duration retention;
    private final Counter prunedCounter;

    /** Spring-wired constructor; retention binds via {@link OAuthStateProperties}. */
    @Autowired
    public OAuthStateNonceCleanupJob(
        OAuthStateNonceRepository repository,
        OAuthStateProperties properties,
        MeterRegistry meterRegistry
    ) {
        this(repository, properties.nonceRetention(), meterRegistry);
    }

    /** Canonical constructor (also the unit-test seam): retention passed directly. */
    public OAuthStateNonceCleanupJob(
        OAuthStateNonceRepository repository,
        Duration retention,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.retention = retention == null ? Duration.ofDays(7) : retention;
        this.prunedCounter = Counter.builder("oauth.state.nonce.pruned")
            .description("Number of OAuth state nonces pruned by the daily sweep")
            .register(meterRegistry);
    }

    /** Runs daily at 04:00 server time. */
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "oauth-state-nonce-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = repository.deleteByIssuedAtBefore(cutoff);
        if (deleted > 0) {
            prunedCounter.increment(deleted);
        }
        log.info(
            "OAuthStateNonceCleanupJob: pruned {} nonces older than {} (retention={})",
            deleted,
            cutoff,
            retention
        );
    }
}
