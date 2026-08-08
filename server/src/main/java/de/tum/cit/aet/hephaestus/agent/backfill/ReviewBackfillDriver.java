package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Walks each confirmed campaign through its scope, one bounded batch per tick.
 *
 * <h2>Why it pauses instead of skipping</h2>
 *
 * <p>The budget is checked once <em>before</em> each batch, and a campaign that finds its purse
 * exhausted stops with the cursor exactly where it was — so the artifacts it has not reached are still
 * owed, and it resumes at the first of them. A campaign that instead carried on and let each submission
 * be refused would leave a baseline in which "not reviewed" and "reviewed, nothing found" are the same
 * absence, and nothing downstream could tell them apart. A truncated baseline is legible; a
 * gap-toothed one is a lie.
 *
 * <p>There is a second guarantee underneath, for the batch that crosses the cap part-way: a submission
 * refused for budget is recorded against its ledger row as {@code BUDGET_EXHAUSTED}, which is a PENDING
 * state, and {@code PendingSignalReaper} re-offers it later. So even the artifacts the cursor walked past
 * during the crossing are re-offered rather than lost — the pause bounds the overshoot, the ledger
 * catches what the overshoot touched.
 *
 * <h2>The artifact that throws</h2>
 *
 * <p>Counted as failed, never as passed. A submission that throws unwinds its own {@code REQUIRES_NEW}
 * transaction and takes its ledger row with it, so there is no artifact-level trace left of it anywhere
 * — which means the run's own counters are the only place the hole can be reported. Folding those
 * artifacts into the passes would make {@code submitted + passed} reach the estimate and the campaign
 * announce COMPLETED, producing by arithmetic exactly the gap-toothed baseline the pause exists to
 * prevent. A campaign that finishes with a non-zero failure count has told the truth about itself.
 *
 * <h2>Resuming</h2>
 *
 * <p>A paused campaign is retried on every tick and returns to RUNNING as soon as the reason clears — a
 * new calendar month, a raised cap, a re-enabled binding. Nothing has to be re-confirmed, because the
 * estimate the admin approved covers the whole scope and pausing did not change it.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Confirmed campaigns are driven for every workspace on the instance")
public class ReviewBackfillDriver {

    private static final Logger log = LoggerFactory.getLogger(ReviewBackfillDriver.class);

    /** How many campaigns one tick advances. Each gets one batch, so no run monopolises the driver. */
    private static final int MAX_RUNS_PER_TICK = 5;

    private final ReviewBackfillRunRepository runRepository;
    private final ReviewBackfillScopeRepository scopeRepository;
    private final ReviewBackfillSubmitter submitter;
    private final WorkspaceAgentBindingRepository bindingRepository;
    private final LlmBudgetService llmBudgetService;
    private final ReviewBackfillProperties properties;

    public ReviewBackfillDriver(
        ReviewBackfillRunRepository runRepository,
        ReviewBackfillScopeRepository scopeRepository,
        ReviewBackfillSubmitter submitter,
        WorkspaceAgentBindingRepository bindingRepository,
        LlmBudgetService llmBudgetService,
        ReviewBackfillProperties properties
    ) {
        this.runRepository = runRepository;
        this.scopeRepository = scopeRepository;
        this.submitter = submitter;
        this.bindingRepository = bindingRepository;
        this.llmBudgetService = llmBudgetService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 2, initialDelay = 2, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "review-backfill-driver", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @WorkspaceAgnostic("Sweeps campaigns across every workspace")
    public void tick() {
        List<ReviewBackfillRun> active = runRepository.findByStatusIn(
            List.of(ReviewBackfillStatus.RUNNING, ReviewBackfillStatus.PAUSED),
            PageRequest.ofSize(MAX_RUNS_PER_TICK)
        );
        for (ReviewBackfillRun run : active) {
            try {
                advance(run);
            } catch (RuntimeException e) {
                // One campaign's failure must not stop the others. What lands here is a whole-run failure
                // — the scope query, or a losing optimistic-lock write because somebody changed the run
                // under us — not a single artifact's, which `advance` walks past itself. The run keeps its
                // persisted cursor, so the next tick re-reads it and resumes from the last batch that
                // committed; the work between that cursor and the failure is re-offered, and the ledger's
                // unique key is what keeps re-offering from re-reviewing.
                log.warn("Review backfill batch failed: runId={}", run.getId(), e);
            }
        }
    }

    /**
     * Advance one campaign by at most one batch.
     *
     * <p>Deliberately holds no transaction of its own. Each artifact's turn opens one
     * ({@link ReviewBackfillSubmitter#offer} is {@code REQUIRES_NEW}) so a single failure cannot unwind
     * the batch around it, and the run's own progress is a single-row write that needs no wider scope.
     * The run arrives with its workspace already fetched, so no lazy association is touched here.
     */
    void advance(ReviewBackfillRun run) {
        if (!run.getStatus().isActive()) {
            return;
        }
        Workspace workspace = run.getWorkspace();

        ReviewBackfillPauseReason blocked = blockingReason(workspace);
        if (blocked != null) {
            if (run.getStatus() != ReviewBackfillStatus.PAUSED || run.getPauseReason() != blocked) {
                log.info("Review backfill paused: runId={}, reason={}", run.getId(), blocked);
                run.transitionTo(ReviewBackfillStatus.PAUSED, blocked);
                runRepository.save(run);
            }
            return;
        }
        if (run.getStatus() == ReviewBackfillStatus.PAUSED) {
            log.info("Review backfill resumed: runId={}", run.getId());
            run.transitionTo(ReviewBackfillStatus.RUNNING, null);
        }

        List<Long> batch = nextBatch(run);
        if (batch.isEmpty()) {
            log.info(
                "Review backfill complete: runId={}, submitted={}, passed={}, failed={}",
                run.getId(),
                run.getSubmittedCount(),
                run.getPassedCount(),
                run.getFailedCount()
            );
            run.transitionTo(ReviewBackfillStatus.COMPLETED, null);
            runRepository.save(run);
            return;
        }

        int submitted = 0;
        int passed = 0;
        int failed = 0;
        long cursor = run.getCursorArtifactId() == null ? 0L : run.getCursorArtifactId();
        for (Long artifactId : batch) {
            ReviewBackfillSubmitter.Outcome outcome = null;
            try {
                outcome = submitter.offer(run, artifactId);
            } catch (RuntimeException e) {
                // The cursor advances past a failure on purpose. Each offer is REQUIRES_NEW, so this
                // artifact's failure has already unwound by itself and nothing behind it is at risk — but
                // if the batch aborted here the cursor would never be written, and the next tick would
                // re-walk these same ids and fail on this same artifact. A deterministically failing
                // artifact would freeze the campaign for good, with the run still reading RUNNING and its
                // counts never moving. One artifact is allowed to be unreviewable; a campaign is not
                // allowed to stop because of it.
                log.warn(
                    "Review backfill artifact failed, walking past it: runId={}, artifactId={}",
                    run.getId(),
                    artifactId,
                    e
                );
                failed++;
            }
            if (outcome == ReviewBackfillSubmitter.Outcome.SUBMITTED) {
                submitted++;
            } else if (outcome != null) {
                passed++;
            }
            cursor = artifactId;
        }
        run.setCursorArtifactId(cursor);
        run.setSubmittedCount(run.getSubmittedCount() + submitted);
        run.setPassedCount(run.getPassedCount() + passed);
        run.setFailedCount(run.getFailedCount() + failed);
        run.setUpdatedAt(java.time.Instant.now());
        runRepository.save(run);
        log.info(
            "Review backfill batch: runId={}, submitted={}, passed={}, failed={}, cursor={}",
            run.getId(),
            submitted,
            passed,
            failed,
            cursor
        );
    }

    /**
     * Why this workspace cannot be spent against right now, or {@code null} if it can.
     *
     * <p>Checked once per batch rather than per artifact. The usage ledger only gains a row when a job
     * ends, so a per-artifact check would not be meaningfully fresher — it would cost one aggregate query
     * per artifact to learn the same thing. The batch size is what bounds the resulting overshoot.
     */
    private @Nullable ReviewBackfillPauseReason blockingReason(Workspace workspace) {
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
            return ReviewBackfillPauseReason.WORKSPACE_UNAVAILABLE;
        }
        if (!Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled())) {
            return ReviewBackfillPauseReason.WORKSPACE_UNAVAILABLE;
        }
        WorkspaceAgentBinding binding = bindingRepository
            .findByWorkspaceIdAndPurposeWithModels(workspace.getId(), AgentPurpose.PRACTICE_REVIEW)
            .filter(WorkspaceAgentBinding::isEnabled)
            .orElse(null);
        if (binding == null) {
            return ReviewBackfillPauseReason.BINDING_DISABLED;
        }
        if (llmBudgetService.blockSubmission(workspace, "REVIEW_BACKFILL", binding.getFundingSource())) {
            return ReviewBackfillPauseReason.BUDGET_EXHAUSTED;
        }
        return null;
    }

    private List<Long> nextBatch(ReviewBackfillRun run) {
        long after = run.getCursorArtifactId() == null ? 0L : run.getCursorArtifactId();
        var page = PageRequest.ofSize(properties.batchSize());
        return ArtifactKinds.PULL_REQUEST.equals(run.kind())
            ? scopeRepository.findPullRequestIds(
                  run.getWorkspace().getId(),
                  run.getFromAt(),
                  run.getToAt(),
                  after,
                  page
              )
            : scopeRepository.findIssueIds(run.getWorkspace().getId(), run.getFromAt(), run.getToAt(), after, page);
    }
}
