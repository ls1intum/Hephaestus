package de.tum.cit.aet.hephaestus.integration.slack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable-dedup integration test (Testcontainers). Each {@code claim} runs in its own committed transaction to
 * model a distinct replica: two replicas receiving the SAME Slack {@code event_id} see exactly one win the
 * first-writer-wins insert, while distinct event ids both win. The TTL sweep drops expired markers.
 *
 * <p>This is the deterministic half of the dedup coverage; the real Slack retry-replay across live replicas is
 * LIVE-only (it needs Slack actually redelivering to two pods).
 */
class SlackEventDedupIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SlackEventDedupRepository repository;

    @Autowired
    private PlatformTransactionManager txManager;

    /** Claim {@code eventId} in its own committed transaction — one simulated replica's attempt. */
    private boolean claimAsReplica(String eventId) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        Instant now = Instant.now();
        return Boolean.TRUE.equals(
            tt.execute(status -> repository.claim(eventId, now, now.plus(48, ChronoUnit.HOURS)) > 0)
        );
    }

    @Test
    void twoReplicas_sameEventId_exactlyOneClaims() {
        String eventId = "Ev-" + UUID.randomUUID();

        boolean replicaA = claimAsReplica(eventId);
        boolean replicaB = claimAsReplica(eventId);

        assertThat(replicaA).isTrue();
        assertThat(replicaB).isFalse();
    }

    @Test
    void distinctEventIds_bothClaim() {
        assertThat(claimAsReplica("Ev-" + UUID.randomUUID())).isTrue();
        assertThat(claimAsReplica("Ev-" + UUID.randomUUID())).isTrue();
    }

    @Test
    void deleteExpired_dropsPastTtlMarkers() {
        String eventId = "Ev-" + UUID.randomUUID();
        TransactionTemplate tt = new TransactionTemplate(txManager);
        Instant now = Instant.now();
        // Insert a marker that is already expired.
        Integer inserted = tt.execute(status ->
            repository.claim(eventId, now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS))
        );
        assertThat(inserted).isEqualTo(1);

        Integer deleted = tt.execute(status -> repository.deleteExpired(now));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
    }
}
