package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMentorDailyBudgetRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorQuotaGuard.Decision;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the fleet daily-budget cap is genuine <em>shared state</em>, not a per-replica counter. Two independent
 * {@link SlackMentorQuotaGuard} instances (standing in for two pods) that share only the same Postgres
 * {@code slack_mentor_daily_budget} row must, between them, allow exactly {@code budget} turns for the day and
 * refuse every one after — the combined draw-down can never reach {@code N × budget}. This is the regression that
 * the previous in-memory {@code budgetUsed} field could not prevent.
 */
class SlackMentorFleetBudgetSharedStateIntegrationTest extends BaseIntegrationTest {

    /** A distinct, far-future day so the shared counter row never collides with other tests on the container. */
    private static final LocalDate DAY = LocalDate.of(2999, 1, 2);

    /** A clock pinned to {@link #DAY} so both "replicas" bucket to the same UTC day row. */
    private static final Clock FIXED = Clock.fixed(DAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private static final int FLEET_BUDGET = 3;

    @Autowired
    private SlackMentorDailyBudgetRepository dailyBudgetRepository;

    @BeforeEach
    void clearThisDaysCounter() {
        dailyBudgetRepository.findById(DAY).ifPresent(dailyBudgetRepository::delete);
    }

    /** A guard with an effectively-unlimited per-user cap, so the fleet budget is the only binding constraint. */
    private SlackMentorQuotaGuard replica() {
        return new SlackMentorQuotaGuard(dailyBudgetRepository, 1_000, FLEET_BUDGET, FIXED);
    }

    @Test
    void twoReplicasSharingTheDbEnforceOneCombinedBudget() {
        SlackMentorQuotaGuard replicaA = replica();
        SlackMentorQuotaGuard replicaB = replica();

        // Interleave turns from distinct users across both replicas so neither per-user nor per-replica counting
        // could mask the shared cap. With FLEET_BUDGET=3, exactly three of these may run fleet-wide.
        int allowed = 0;
        allowed += (replicaA.tryAcquire("ws:u1") == Decision.ALLOWED) ? 1 : 0;
        allowed += (replicaB.tryAcquire("ws:u2") == Decision.ALLOWED) ? 1 : 0;
        allowed += (replicaA.tryAcquire("ws:u3") == Decision.ALLOWED) ? 1 : 0;

        // Budget is now exhausted; further turns on EITHER replica are refused with the budget decision.
        Decision fourthOnB = replicaB.tryAcquire("ws:u4");
        Decision fifthOnA = replicaA.tryAcquire("ws:u5");

        assertThat(allowed).as("exactly the fleet budget of turns were allowed across both replicas").isEqualTo(3);
        assertThat(fourthOnB).isEqualTo(Decision.DAILY_BUDGET_EXCEEDED);
        assertThat(fifthOnA).isEqualTo(Decision.DAILY_BUDGET_EXCEEDED);

        // The shared row reflects the combined draw-down: capped at the budget, never 2× it.
        assertThat(dailyBudgetRepository.findById(DAY).orElseThrow().getUsed()).isEqualTo(FLEET_BUDGET);
    }

    @Test
    void tryConsumeIsAtomicAndBudgetBounded_underConcurrentReplicas() throws Exception {
        // Hammer the same day row from many threads (each its own guard = its own "replica") with a budget far below
        // the attempt count. The atomic INSERT … ON CONFLICT … WHERE used < :budget must let exactly FLEET_BUDGET
        // through, proving no lost updates under contention.
        // Kept under the integration Hikari pool size (10) so every worker gets a connection promptly.
        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var allowed = new java.util.concurrent.atomic.AtomicInteger();
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threads; i++) {
                final int id = i;
                futures.add(
                    pool.submit(() -> {
                        SlackMentorQuotaGuard replica = replica();
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (replica.tryAcquire("ws:concurrent-" + id) == Decision.ALLOWED) {
                            allowed.incrementAndGet();
                        }
                    })
                );
            }
            start.countDown();
            for (var f : futures) {
                f.get();
            }

            assertThat(allowed.get())
                .as("atomic shared counter admits exactly the budget under contention")
                .isEqualTo(FLEET_BUDGET);
            assertThat(dailyBudgetRepository.findById(DAY).orElseThrow().getUsed()).isEqualTo(FLEET_BUDGET);
        } finally {
            pool.shutdownNow();
        }
    }
}
