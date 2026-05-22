package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-tier JWT revocation check: Caffeine absorbs the read storm of WSS upgrades, Postgres is the
 * durable source of truth. Revocation propagates within {@link #CACHE_TTL} (5 minutes) — accepted
 * for the BYO threat model where compromise is handled by rotating registration tokens rather
 * than relying on near-real-time revocation.
 */
public class WorkerTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(WorkerTokenDenylist.class);
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final WorkerTokenDenylistRepository repository;
    private final Cache<String, Boolean> cache;

    public WorkerTokenDenylist(WorkerTokenDenylistRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(CACHE_TTL)
            .build();
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        // Single-flight load: concurrent isRevoked() for the same jti on a cold cache collapse
        // into one Postgres query — Caffeine coalesces parallel loaders by key.
        Boolean revoked = cache.get(jti, repository::existsById);
        return Boolean.TRUE.equals(revoked);
    }

    @Transactional
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti must not be blank");
        }
        repository.save(new WorkerTokenDenylistEntry(jti, Instant.now(), expiresAt));
        cache.put(jti, Boolean.TRUE);
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void sweepExpired() {
        int removed = repository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Pruned {} expired worker-token denylist row(s)", removed);
        }
    }
}
