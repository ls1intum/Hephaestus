package de.tum.cit.aet.hephaestus.integration.slack.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMentorDailyBudgetRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackMentorQuotaGuard.Decision;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Quota-guard unit tests. These pin the <em>in-memory per-user</em> cap and the wiring/ordering between it and the
 * shared fleet-budget counter, using a mocked {@link SlackMentorDailyBudgetRepository}: a user already at their cap
 * must never draw down the shared budget, and an exhausted fleet budget must not consume the per-user tally. The
 * fleet cap itself is genuinely shared state, so its cross-replica enforcement is proven end-to-end against a real
 * database in {@code SlackMentorFleetBudgetSharedStateIntegrationTest}.
 */
class SlackMentorQuotaGuardTest extends BaseUnitTest {

    private static final Instant DAY1 = Instant.parse("2026-07-03T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-07-04T00:30:00Z");

    @Mock
    private SlackMentorDailyBudgetRepository dailyBudgetRepository;

    @BeforeEach
    void budgetAlwaysAvailableByDefault() {
        // Default: the shared budget always has room, so these tests isolate the in-memory per-user behaviour.
        // Lenient because the budget-exhaustion tests below re-stub tryConsume to return 0.
        lenient().when(dailyBudgetRepository.tryConsume(any(LocalDate.class), anyInt())).thenReturn(1);
    }

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

    private SlackMentorQuotaGuard guard(int perUserCap, int budget, Clock clock) {
        return new SlackMentorQuotaGuard(dailyBudgetRepository, perUserCap, budget, clock);
    }

    @Test
    void allowsUpToPerUserCap_thenReportsUserCapExceeded() {
        SlackMentorQuotaGuard guard = guard(2, 100, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);
    }

    @Test
    void perUserCapIsPerUser_notShared() {
        SlackMentorQuotaGuard guard = guard(1, 100, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:bob")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);
    }

    @Test
    void userCapExceeded_doesNotDrawDownTheSharedFleetBudget() {
        // Ordering invariant: the per-user cap is checked first, so a user already at their cap must not consume a
        // unit of the shared budget. Remove the early return and this fails (tryConsume would be called for the 2nd).
        SlackMentorQuotaGuard guard = guard(1, 100, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);

        // Exactly one budget draw-down happened (the first, allowed turn) — the capped turn drew nothing.
        verify(dailyBudgetRepository).tryConsume(LocalDate.of(2026, 7, 3), 100);
    }

    @Test
    void fleetBudgetExhausted_reportsBudgetExceeded_andDoesNotConsumeThePerUserTally() {
        // The shared counter says "no budget" (0), so the turn is refused without touching the per-user tally: once
        // the budget frees up, the user still has their full allowance. This is the DAILY_BUDGET_EXCEEDED branch.
        when(dailyBudgetRepository.tryConsume(any(LocalDate.class), anyInt())).thenReturn(0, 1);
        SlackMentorQuotaGuard guard = guard(1, 5, new MutableClock(DAY1));

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.DAILY_BUDGET_EXCEEDED);
        // Budget now available again → the same user is still under their (untouched) per-user cap of 1.
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void perUserCounterResetsOnUtcDayRoll() {
        MutableClock clock = new MutableClock(DAY1);
        SlackMentorQuotaGuard guard = guard(1, 100, clock);

        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.USER_CAP_EXCEEDED);

        clock.now = DAY2;
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.ALLOWED);
        // The shared counter is queried with the NEW day's key after the roll.
        verify(dailyBudgetRepository).tryConsume(LocalDate.of(2026, 7, 4), 100);
    }

    @Test
    void overCapReturnsADecision_neverThrows() {
        when(dailyBudgetRepository.tryConsume(any(LocalDate.class), anyInt())).thenReturn(0);
        SlackMentorQuotaGuard guard = guard(50, 0, new MutableClock(DAY1));

        // A zero fleet budget refuses every turn with a Decision (the caller turns it into a friendly reply).
        assertThat(guard.tryAcquire("ws:alice")).isEqualTo(Decision.DAILY_BUDGET_EXCEEDED);
        verify(dailyBudgetRepository, never()).save(any());
    }
}
