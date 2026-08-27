package de.tum.cit.aet.hephaestus.agent.backfill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The tick itself, which is deliberately almost nothing: select, delegate, and survive.
 *
 * <p>The two behaviours worth pinning are both about failure. One workspace's broken sweep must not stop
 * the rest, and it must not retry itself to death either — a failed turn rolls back its own advance, so
 * without an explicit deferral the same schedule would come due again in five minutes, for ever.
 */
@DisplayName("Review sweep scheduler")
class ReviewSweepSchedulerTest extends BaseUnitTest {

    private static final UUID FIRST = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    @Mock
    private ReviewSweepScheduleRepository scheduleRepository;

    @Mock
    private ReviewSweepCampaignOpener opener;

    private ReviewSweepScheduler scheduler() {
        return new ReviewSweepScheduler(scheduleRepository, opener);
    }

    @Test
    void oneWorkspacesFailedSweepDoesNotStopTheNext() {
        when(scheduleRepository.findDue(any(), any())).thenReturn(List.of(schedule(FIRST), schedule(SECOND)));
        when(opener.openDueRun(eq(FIRST), any())).thenThrow(new IllegalStateException("scope query blew up"));
        when(opener.openDueRun(eq(SECOND), any())).thenReturn(ReviewSweepOutcome.OPENED);

        scheduler().tick();

        verify(opener).openDueRun(eq(SECOND), any());
    }

    @Test
    void aSweepThatThrewIsPushedToItsNextOccurrenceInsteadOfRetryingEveryFiveMinutes() {
        when(scheduleRepository.findDue(any(), any())).thenReturn(List.of(schedule(FIRST)));
        when(opener.openDueRun(eq(FIRST), any())).thenThrow(new IllegalStateException("scope query blew up"));

        scheduler().tick();

        verify(opener).deferAfterFailure(eq(FIRST), any(Instant.class));
    }

    /** A deferral that fails too must not take the whole tick down with it. */
    @Test
    void aFailedDeferralIsSwallowedSoTheRemainingSchedulesStillRun() {
        when(scheduleRepository.findDue(any(), any())).thenReturn(List.of(schedule(FIRST), schedule(SECOND)));
        when(opener.openDueRun(eq(FIRST), any())).thenThrow(new IllegalStateException("scope query blew up"));
        doThrow(new IllegalStateException("and so did the deferral"))
                .when(opener)
                .deferAfterFailure(eq(FIRST), any());
        when(opener.openDueRun(eq(SECOND), any())).thenReturn(ReviewSweepOutcome.OPENED);

        scheduler().tick();

        verify(opener).openDueRun(eq(SECOND), any());
    }

    private static ReviewSweepSchedule schedule(UUID id) {
        ReviewSweepSchedule schedule = new ReviewSweepSchedule();
        schedule.setId(id);
        return schedule;
    }
}
