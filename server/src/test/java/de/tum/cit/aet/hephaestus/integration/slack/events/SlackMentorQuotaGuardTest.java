package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorQuotaGuard.Decision;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Quota-guard unit tests: the per-user turn cap and the fleet daily-budget cap each trip at the right count and
 * return a friendly {@link Decision} (never throw), and both tallies reset when the UTC day rolls over.
 */
class SlackMentorQuotaGuardTest extends BaseUnitTest {

    private static final Instant DAY1 = Instant.parse("2026-07-03T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-07-04T00:30:00Z");

    private static class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void allowsUpToPerUserCap_thenReportsUserCapExceeded() {
        SlackMentorQuotaGuard guard = new SlackMentorQuotaGuard(2, 100, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);
    }

    @Test
    void perUserCapIsPerUser_notShared() {
        SlackMentorQuotaGuard guard = new SlackMentorQuotaGuard(1, 100, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:bob")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);
    }

    @Test
    void dailyBudgetExhausted_reportsBudgetExceededForAnUnderCapUser() {
        // Generous per-user cap, tiny global budget: the budget is the binding constraint.
        SlackMentorQuotaGuard guard = new SlackMentorQuotaGuard(100, 2, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:bob")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:carol")).isEqualTo(Decision.DAILY_BUDGET_EXCEEDED);
    }

    @Test
    void countersResetOnUtcDayRoll() {
        MutableClock clock = new MutableClock(DAY1);
        SlackMentorQuotaGuard guard = new SlackMentorQuotaGuard(1, 100, clock);

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);

        clock.now = DAY2;
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
    }
}
