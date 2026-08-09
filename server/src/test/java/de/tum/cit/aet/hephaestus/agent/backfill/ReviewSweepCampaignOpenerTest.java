package de.tum.cit.aet.hephaestus.agent.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * One due schedule's turn.
 *
 * <p>What matters here is what is <em>not</em> created. A sweep that opened a campaign on every tick
 * regardless would stack a paused run per night against a suspended workspace, block the workspace's one
 * campaign slot, and pay to price a scope nobody can review.
 */
@DisplayName("Review sweep campaign opener")
class ReviewSweepCampaignOpenerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 4L;
    private static final long ACCOUNT_ID = 91L;
    private static final UUID SCHEDULE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-09T02:00:00Z");

    @Mock
    private ReviewSweepScheduleRepository scheduleRepository;

    @Mock
    private ReviewBackfillRunRepository runRepository;

    @Mock
    private ReviewBackfillScopeRepository scopeRepository;

    @Mock
    private ReviewBackfillCostEstimator costEstimator;

    @Mock
    private ConfigAuditPort configAudit;

    private final ReviewBackfillProperties properties = new ReviewBackfillProperties(
        25,
        Duration.ofDays(400),
        5000,
        Duration.ofDays(90)
    );

    private ReviewSweepCampaignOpener opener() {
        return new ReviewSweepCampaignOpener(
            scheduleRepository,
            runRepository,
            scopeRepository,
            costEstimator,
            properties,
            configAudit
        );
    }

    /**
     * The whole feature in one assertion: a due schedule produces a campaign that is already RUNNING,
     * says a schedule opened it, and is attributed to the account that set the schedule up. Directly
     * RUNNING because creating the schedule was the confirmation — a nightly sweep that waited for a
     * click would be a queue of unconfirmed runs nobody clears.
     */
    @Test
    void aDueScheduleOpensARunningCampaignStampedAsASweep() {
        ReviewSweepSchedule schedule = schedule();
        givenSchedule(schedule);
        when(scopeRepository.countPullRequests(eq(WORKSPACE_ID), any(), any())).thenReturn(4L);
        when(costEstimator.estimateTotalUsd(eq(WORKSPACE_ID), any(), anyInt())).thenReturn(new BigDecimal("1.20"));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.OPENED);

        ArgumentCaptor<ReviewBackfillRun> run = ArgumentCaptor.forClass(ReviewBackfillRun.class);
        verify(runRepository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(ReviewBackfillStatus.RUNNING);
        assertThat(run.getValue().getDiscoveredVia()).isEqualTo(DiscoveredVia.SWEEP);
        assertThat(run.getValue().getSweepScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(run.getValue().getConfirmedByAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(run.getValue().getFromAt()).isEqualTo(NOW.minus(Duration.ofDays(2)));
        assertThat(run.getValue().getToAt()).isEqualTo(NOW);
        verify(configAudit).record(any(ConfigAuditEntry.class));
    }

    /** Opening a campaign is what {@code lastRunAt} records, and the occurrence moves on either way. */
    @Test
    void openingACampaignRecordsItAndMovesToTheNextOccurrence() {
        ReviewSweepSchedule schedule = schedule();
        givenSchedule(schedule);
        when(scopeRepository.countPullRequests(eq(WORKSPACE_ID), any(), any())).thenReturn(1L);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        opener().openDueRun(SCHEDULE_ID, NOW);

        assertThat(schedule.getLastRunAt()).isEqualTo(NOW);
        assertThat(schedule.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        verify(scheduleRepository).save(schedule);
    }

    /**
     * {@code lastRunAt} is the only thing that tells a working schedule from one whose every turn has
     * been skipped for a month. A tick that opened nothing must leave it alone, or that distinction is
     * unreadable on the screen where an admin would notice.
     */
    @Test
    void aTickThatOpenedNothingDoesNotClaimToHaveRun() {
        ReviewSweepSchedule schedule = schedule();
        givenSchedule(schedule);
        when(scopeRepository.countPullRequests(eq(WORKSPACE_ID), any(), any())).thenReturn(0L);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_EMPTY_SCOPE);

        assertThat(schedule.getLastRunAt()).isNull();
        assertThat(schedule.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        verify(runRepository, never()).save(any());
    }

    /**
     * One campaign per workspace at a time, as for a hand-scoped backfill. Two overlapping runs would
     * each read the other's ledger rows as already covered, so neither would cover its own scope.
     */
    @Test
    void aWorkspaceStillWorkingThroughACampaignDoesNotGetASecond() {
        givenSchedule(schedule());
        when(runRepository.existsByWorkspaceIdAndStatusIn(eq(WORKSPACE_ID), any())).thenReturn(true);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_CAMPAIGN_UNDER_WAY);

        verify(runRepository, never()).save(any());
        Mockito.verifyNoInteractions(scopeRepository, costEstimator, configAudit);
    }

    @Test
    void aSuspendedWorkspaceDoesNotAccumulateAPausedCampaignPerNight() {
        ReviewSweepSchedule schedule = schedule();
        schedule.getWorkspace().setStatus(Workspace.WorkspaceStatus.SUSPENDED);
        givenSchedule(schedule);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_WORKSPACE_UNAVAILABLE);

        verify(runRepository, never()).save(any());
    }

    @Test
    void aWorkspaceWithPracticesSwitchedOffIsNotSwept() {
        ReviewSweepSchedule schedule = schedule();
        schedule.getWorkspace().getFeatures().setPracticesEnabled(false);
        givenSchedule(schedule);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_WORKSPACE_UNAVAILABLE);

        verify(runRepository, never()).save(any());
    }

    /**
     * A workspace that produced more work in two days than a whole hand-confirmed campaign may cover has
     * a broken mirror, and a nightly sweep is the wrong place to find that out by spending a month's
     * budget on it.
     */
    @Test
    void anImplausibleScopeIsRefusedRatherThanPaidFor() {
        givenSchedule(schedule());
        when(scopeRepository.countPullRequests(eq(WORKSPACE_ID), any(), any())).thenReturn(9_000L);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_SCOPE_TOO_LARGE);

        verify(runRepository, never()).save(any());
        Mockito.verifyNoInteractions(costEstimator);
    }

    @Test
    void aScheduleSwitchedOffBetweenSelectionAndActionOpensNothing() {
        ReviewSweepSchedule schedule = schedule();
        schedule.setEnabled(false);
        givenSchedule(schedule);

        assertThat(opener().openDueRun(SCHEDULE_ID, NOW)).isEqualTo(ReviewSweepOutcome.SKIPPED_DISABLED);

        verify(runRepository, never()).save(any());
    }

    /**
     * The deferral exists so a deterministically failing sweep does not come due every five minutes for
     * ever, failing in the same way at the same cost each time.
     */
    @Test
    void aFailedTurnIsDeferredToTheNextOccurrenceWithoutOpeningAnything() {
        ReviewSweepSchedule schedule = schedule();
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        opener().deferAfterFailure(SCHEDULE_ID, NOW);

        assertThat(schedule.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(schedule.getLastRunAt()).isNull();
        verify(runRepository, never()).save(any());
    }

    private void givenSchedule(ReviewSweepSchedule schedule) {
        when(scheduleRepository.findByIdWithWorkspace(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ReviewSweepSchedule schedule() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.getFeatures().setPracticesEnabled(true);

        ReviewSweepSchedule schedule = new ReviewSweepSchedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setWorkspace(workspace);
        schedule.setArtifactKind(ArtifactKinds.PULL_REQUEST.value());
        schedule.setCadence(ReviewSweepCadence.DAILY);
        schedule.setLookbackDays(2);
        schedule.setEnabled(true);
        schedule.setNextRunAt(NOW);
        schedule.setCreatedByAccountId(ACCOUNT_ID);
        return schedule;
    }
}
