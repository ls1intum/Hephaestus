package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one due {@link ReviewSweepSchedule} into one {@link ReviewBackfillRun}.
 *
 * <p>Kept apart from {@code ReviewBackfillService}, whose whole promise is that it can be called without a
 * review ever running: every method there ends at {@code AWAITING_CONFIRMATION} and waits for a human. This
 * one starts a campaign outright, which is worth a class boundary rather than a parameter.
 *
 * <p>The run is created directly in {@code RUNNING} and confirmed by the account that created the
 * schedule: the schedule row already is the standing decision — it names the cadence, the window and the
 * account that authorised them — so waiting for a confirmation click would just queue up unconfirmed runs
 * nobody clears.
 *
 * <p>Everything after opening is unchanged campaign machinery: {@code ReviewBackfillDriver} paces the
 * walk, pauses on an exhausted budget or a disabled binding, and isolates each artifact.
 */
@Service
@WorkspaceAgnostic("Opens campaigns for whichever workspace's schedule came due")
public class ReviewSweepCampaignOpener {

    private static final Logger log = LoggerFactory.getLogger(ReviewSweepCampaignOpener.class);

    /** A campaign is under way while it is in one of these; a second one must not start beside it. */
    private static final List<ReviewBackfillStatus> UNDER_WAY =
            List.of(ReviewBackfillStatus.RUNNING, ReviewBackfillStatus.PAUSED);

    private final ReviewSweepScheduleRepository scheduleRepository;
    private final ReviewBackfillRunRepository runRepository;
    private final ReviewBackfillScopeRepository scopeRepository;
    private final ReviewBackfillCostEstimator costEstimator;
    private final ReviewBackfillProperties properties;
    private final ConfigAuditPort configAudit;

    public ReviewSweepCampaignOpener(
            ReviewSweepScheduleRepository scheduleRepository,
            ReviewBackfillRunRepository runRepository,
            ReviewBackfillScopeRepository scopeRepository,
            ReviewBackfillCostEstimator costEstimator,
            ReviewBackfillProperties properties,
            ConfigAuditPort configAudit) {
        this.scheduleRepository = scheduleRepository;
        this.runRepository = runRepository;
        this.scopeRepository = scopeRepository;
        this.costEstimator = costEstimator;
        this.properties = properties;
        this.configAudit = configAudit;
    }

    /**
     * Open this schedule's sweep, and move it on to its next occurrence either way.
     *
     * <p>Both writes share one transaction: they touch the same optimistically-locked row, and a run that
     * committed while its schedule stayed due would be re-opened on the following tick.
     *
     * <p>{@code lastRunAt} moves only when a campaign was actually opened — it anchors the next window, so
     * moving it after a tick that looked at nothing would declare the days in between already covered.
     */
    @Transactional
    public ReviewSweepOutcome openDueRun(UUID scheduleId, Instant now) {
        ReviewSweepSchedule schedule =
                scheduleRepository.findByIdWithWorkspace(scheduleId).orElse(null);
        if (schedule == null) {
            // Deleted between selection and action. Nothing to advance, nothing to open.
            return ReviewSweepOutcome.SKIPPED_DISABLED;
        }
        ReviewSweepOutcome outcome = open(schedule, now);
        if (outcome == ReviewSweepOutcome.OPENED) {
            schedule.setLastRunAt(now);
        }
        schedule.advancePast(now);
        scheduleRepository.save(schedule);
        return outcome;
    }

    /**
     * Move a schedule past its due time without opening anything, after its turn threw. Its own transaction
     * because the failed one has already rolled back — otherwise a schedule that fails deterministically
     * would come due again on every tick forever.
     */
    @Transactional
    public void deferAfterFailure(UUID scheduleId, Instant now) {
        scheduleRepository.findById(scheduleId).ifPresent(schedule -> {
            schedule.advancePast(now);
            scheduleRepository.save(schedule);
        });
    }

    private ReviewSweepOutcome open(ReviewSweepSchedule schedule, Instant now) {
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            return ReviewSweepOutcome.SKIPPED_DISABLED;
        }
        Workspace workspace = schedule.getWorkspace();
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE
                || !Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled())) {
            // The driver would pause such a run on its first tick anyway; not opening it keeps a
            // suspended workspace from accumulating one blocked, never-running campaign per night.
            return ReviewSweepOutcome.SKIPPED_WORKSPACE_UNAVAILABLE;
        }
        if (runRepository.existsByWorkspaceIdAndStatusIn(workspace.getId(), UNDER_WAY)) {
            return ReviewSweepOutcome.SKIPPED_CAMPAIGN_UNDER_WAY;
        }

        ArtifactKind kind = schedule.kind();
        AgentJobType jobType = ReviewBackfillService.jobTypeFor(kind);
        Instant fromAt = schedule.windowStart(now);
        long inScope = countScope(workspace.getId(), kind, fromAt, now);
        if (inScope == 0) {
            return ReviewSweepOutcome.SKIPPED_EMPTY_SCOPE;
        }
        if (inScope > properties.maxArtifacts()) {
            log.warn(
                    "Review sweep refused an implausible scope: scheduleId={}, workspaceId={}, kind={}, artifacts={}, limit={}",
                    schedule.getId(),
                    workspace.getId(),
                    kind.value(),
                    inScope,
                    properties.maxArtifacts());
            return ReviewSweepOutcome.SKIPPED_SCOPE_TOO_LARGE;
        }

        BigDecimal estimate = costEstimator.estimateTotalUsd(workspace.getId(), jobType, (int) inScope);
        ReviewBackfillRun run = new ReviewBackfillRun();
        run.setWorkspace(workspace);
        run.setArtifactKind(kind.value());
        run.setDiscoveredVia(DiscoveredVia.SWEEP);
        run.setSweepScheduleId(schedule.getId());
        run.setFromAt(fromAt);
        run.setToAt(now);
        run.setEstimatedArtifacts((int) inScope);
        run.setEstimatedCostUsd(estimate);
        run.setStartedAt(now);
        run.setRequestedByAccountId(schedule.getCreatedByAccountId());
        run.setConfirmedByAccountId(schedule.getCreatedByAccountId());
        run.transitionTo(ReviewBackfillStatus.RUNNING, null);
        ReviewBackfillRun saved = runRepository.save(run);

        configAudit.record(ConfigAuditEntry.created(
                ConfigAuditEntityType.REVIEW_BACKFILL_RUN,
                saved.getId(),
                workspace.getId(),
                ReviewBackfillSnapshot.of(saved)));
        log.info(
                "Review sweep opened: scheduleId={}, runId={}, workspaceId={}, kind={}, from={}, to={}, artifacts={}, estimateUsd={}",
                schedule.getId(),
                saved.getId(),
                workspace.getId(),
                kind.value(),
                fromAt,
                now,
                inScope,
                estimate);
        return ReviewSweepOutcome.OPENED;
    }

    private long countScope(Long workspaceId, ArtifactKind kind, Instant fromAt, Instant toAt) {
        return ArtifactKinds.PULL_REQUEST.equals(kind)
                ? scopeRepository.countPullRequests(workspaceId, fromAt, toAt)
                : scopeRepository.countIssues(workspaceId, fromAt, toAt);
    }
}
