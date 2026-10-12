package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomyPolicy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which results reach the artifact itself, by applying {@link PracticeAutonomyPolicy} to the
 * {@link FeedbackChannel#IN_CONTEXT} channel: a result is posted only if its practice autonomy
 * admits the channel <em>and</em> the run's provenance does.
 *
 * <p>Runs strictly after the results are persisted and stamped with their result keys — a
 * {@code HUMAN_APPROVAL} practice and a backfill are measured and recorded exactly like an engaged live run, and
 * differ only in how far the result travels. Nothing here touches the behaviour time series.
 *
 * <p>The provenance rule keeps a backfill campaign from commenting on merged pull requests, where every
 * subscriber would be notified about work nobody can act on; it's checked once per job since a job has
 * exactly one origin.
 *
 * <p>Each withheld result gets a SUPPRESSED ledger row rather than being dropped in silence, so a later
 * evaluation can tell a deliberate quiet from a review that missed it. Writing the row is best-effort: a ledger
 * failure never blocks the delivery of the results that survived.
 *
 * <p>A slug the catalogue read does not resolve is kept when only autonomy would have withheld it — it is
 * never an unknown practice ({@code ReviewOutputService.deliver} refuses those first), only a
 * practice renamed mid-review. Dropping it would cost a developer feedback over a rename, so it is logged
 * instead.
 */
@Component
class InContextDeliveryGate {

    private static final Logger log = LoggerFactory.getLogger(InContextDeliveryGate.class);

    private final PracticeRepository practiceRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;
    private final WorkspaceRepository workspaceRepository;

    InContextDeliveryGate(
            PracticeRepository practiceRepository,
            PracticeResultRepository practiceResultRepository,
            FeedbackLedgerRecorder feedbackLedgerRecorder,
            WorkspaceReviewDefaultsProvider workspaceDefaults,
            WorkspaceRepository workspaceRepository) {
        this.practiceRepository = practiceRepository;
        this.practiceResultRepository = practiceResultRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.workspaceDefaults = workspaceDefaults;
        this.workspaceRepository = workspaceRepository;
    }

    /** The subset of {@code practiceResults} that may be placed on the artifact, in the order given. */
    @Transactional(readOnly = true)
    public List<ValidatedResult> admitInContext(AgentJob job, List<ValidatedResult> practiceResults) {
        if (practiceResults.isEmpty()
                || job.getWorkspace() == null
                || job.getWorkspace().getId() == null) {
            return practiceResults;
        }
        long workspaceId = job.getWorkspace().getId();
        FeedbackSuppressionReason rolloutRefusal =
                refusalIfRolloutRevisionMoved(job.getPracticeRolloutRevision(), workspaceId);
        if (rolloutRefusal != null) {
            recordWithheld(job, practiceResults, rolloutRefusal);
            return List.of();
        }
        ResultOrigin origin = ReviewOutputService.originOf(job.getMetadata());
        if (!origin.delivers(FeedbackChannel.IN_CONTEXT)) {
            log.info(
                    "Provenance withheld all {} result(s) from the artifact: origin={}, jobId={}",
                    practiceResults.size(),
                    origin,
                    job.getId());
            recordWithheld(job, practiceResults, FeedbackSuppressionReason.BACKFILL_QUIET);
            return List.of();
        }

        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        Map<String, PracticeAutonomy> autonomyBySlug = new HashMap<>();
        for (Practice practice : practiceRepository.findByWorkspaceId(workspaceId)) {
            autonomyBySlug.put(
                    practice.getSlug(), AutonomyResolver.effectiveAutonomyOf(practice, defaults.defaultAutonomy()));
        }

        List<ValidatedResult> admitted = new ArrayList<>(practiceResults.size());
        List<ValidatedResult> withheld = new ArrayList<>();
        for (ValidatedResult practiceResult : practiceResults) {
            PracticeAutonomy autonomy = autonomyBySlug.get(practiceResult.practiceSlug());
            if (autonomy == null) {
                log.warn(
                        "No autonomy resolved for result; withholding it: slug={}, jobId={}",
                        practiceResult.practiceSlug(),
                        job.getId());
            }
            if (PracticeAutonomyPolicy.delivers(origin, autonomy, FeedbackChannel.IN_CONTEXT)) {
                admitted.add(practiceResult);
            } else if (autonomy != PracticeAutonomy.HUMAN_APPROVAL) {
                withheld.add(practiceResult);
            }
        }
        if (withheld.isEmpty()) {
            return admitted.size() == practiceResults.size() ? practiceResults : List.copyOf(admitted);
        }
        log.info(
                "Practice autonomy withheld {} of {} result(s) from the artifact: jobId={}",
                withheld.size(),
                practiceResults.size(),
                job.getId());
        recordWithheld(job, withheld, FeedbackSuppressionReason.PRACTICE_REQUIRES_APPROVAL, inCurrentTransaction);
        return admitted;
    }

    @Transactional(readOnly = true)
    public List<ValidatedResult> awaitingApproval(AgentJob job, List<ValidatedResult> practiceResults) {
        if (practiceResults.isEmpty()
                || job.getWorkspace() == null
                || job.getWorkspace().getId() == null) return List.of();
        long workspaceId = job.getWorkspace().getId();
        if (refusalIfRolloutRevisionMoved(job.getPracticeRolloutRevision(), workspaceId) != null) return List.of();
        ResultOrigin origin = ReviewOutputService.originOf(job.getMetadata());
        if (!origin.delivers(FeedbackChannel.IN_CONTEXT)) return List.of();
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        Map<String, PracticeAutonomy> autonomyBySlug = new HashMap<>();
        for (Practice practice : practiceRepository.findByWorkspaceId(workspaceId)) {
            autonomyBySlug.put(
                    practice.getSlug(), AutonomyResolver.effectiveAutonomyOf(practice, defaults.defaultAutonomy()));
        }
        return practiceResults.stream()
                .filter(practiceResult ->
                        autonomyBySlug.get(practiceResult.practiceSlug()) == PracticeAutonomy.HUMAN_APPROVAL)
                .toList();
    }

    private void recordWithheld(AgentJob job, List<ValidatedResult> withheld, FeedbackSuppressionReason reason) {
        Map<String, PracticeResult> byOccurrence = new HashMap<>();
        for (PracticeResult practiceResult : practiceResultRepository.findByAgentJobId(
                job.getId(), job.getWorkspace().getId())) {
            byOccurrence.put(practiceResult.getOccurrenceKey(), practiceResult);
        }
        int index = 0;
        for (ValidatedResult measured : withheld) {
            // Overflowing the band would address the NEXT band's unit, which the (agent_job_id, position)
            // guard would then read as "already recorded" and drop.
            if (index >= FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH) {
                log.warn(
                        "Withheld-feedback ledger band full at {} rows; remaining withheld results are unrecorded: jobId={}",
                        index,
                        job.getId());
                return;
            }
            String occurrenceKey = measured.occurrenceKey();
            if (occurrenceKey == null) {
                continue; // never persisted, so there is no result for a ledger row to bind
            }
            PracticeResult practiceResult = byOccurrence.get(occurrenceKey);
            if (practiceResult == null) {
                continue;
            }
            if (inCurrentTransaction) {
                // Part of the caller's whole outcome: a failed row fails it rather than leaving it incomplete.
                feedbackLedgerRecorder.recordWithheldInCurrentTransaction(job, observation, reason, index++);
                continue;
            }
            try {
                feedbackLedgerRecorder.recordWithheld(job, practiceResult, reason, index++);
            } catch (RuntimeException e) {
                log.warn("Withheld-feedback ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
            }
        }
    }

    private @Nullable FeedbackSuppressionReason refusalIfRolloutRevisionMoved(
            @Nullable Long admittedRevision, long workspaceId) {
        Long revisionInForceNow = workspaceRepository
                .findById(workspaceId)
                .map(workspace -> workspace.getReviewSettings().getRolloutRevision())
                .orElse(null);
        if (admittedRevision == null
                || revisionInForceNow == null
                || admittedRevision.longValue() != revisionInForceNow.longValue()) {
            return FeedbackSuppressionReason.STALE_ROLLOUT_REVISION;
        }
        return null;
    }
}
