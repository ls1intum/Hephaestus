package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.FeedbackAdmission;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which findings reach the artifact itself, by applying {@link FeedbackAdmission} to the
 * {@link FeedbackChannel#IN_CONTEXT} channel: a finding is posted only if its practice's autonomy tier
 * admits the channel <em>and</em> the run's provenance does.
 *
 * <p>Runs strictly after the findings are persisted and stamped with their observation keys — a
 * {@code PROPOSE} practice and a backfill are measured and recorded exactly like an engaged live run, and
 * differ only in how far the result travels. Nothing here touches the behaviour time series.
 *
 * <p>The provenance rule keeps a backfill campaign from commenting on merged pull requests, where every
 * subscriber would be notified about work nobody can act on; it's checked once per job since a job has
 * exactly one origin.
 *
 * <p>Each withheld finding gets a SUPPRESSED ledger row rather than being dropped in silence, so a later
 * evaluation can tell a deliberate quiet from a detection miss. Writing the row is best-effort: a ledger
 * failure never blocks the delivery of the findings that survived.
 *
 * <p>A slug the catalogue read does not resolve is kept when only the tier would have withheld it — it is
 * never an unknown practice ({@code PracticeDetectionDeliveryService.deliver} refuses those first), only a
 * practice renamed mid-review. Dropping it would cost a developer feedback over a rename, so it is logged
 * instead.
 */
@Component
class InContextDeliveryGate {

    private static final Logger log = LoggerFactory.getLogger(InContextDeliveryGate.class);

    private final PracticeRepository practiceRepository;
    private final ObservationRepository observationRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    InContextDeliveryGate(
        PracticeRepository practiceRepository,
        ObservationRepository observationRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        WorkspaceReviewDefaultsProvider workspaceDefaults
    ) {
        this.practiceRepository = practiceRepository;
        this.observationRepository = observationRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.workspaceDefaults = workspaceDefaults;
    }

    /** The subset of {@code findings} that may be placed on the artifact, in the order given. */
    @Transactional(readOnly = true)
    List<ValidatedFinding> admitInContext(AgentJob job, List<ValidatedFinding> findings) {
        if (findings.isEmpty() || job.getWorkspace() == null || job.getWorkspace().getId() == null) {
            return findings;
        }
        ObservationOrigin origin = PracticeDetectionDeliveryService.originOf(job.getMetadata());
        if (!origin.delivers(FeedbackChannel.IN_CONTEXT)) {
            log.info(
                "Provenance withheld all {} finding(s) from the artifact: origin={}, jobId={}",
                findings.size(),
                origin,
                job.getId()
            );
            recordWithheld(job, findings, FeedbackSuppressionReason.BACKFILL_QUIET);
            return List.of();
        }

        // Resolved from the workspace ID, not job.getWorkspace(): the job reaches this gate detached on some
        // paths, so reading a lazy association here would depend on whether the caller holds a session.
        // Tiers go through ReviewTierResolver rather than the raw column, since a practice with no opinion of
        // its own must inherit its area's or workspace's rather than reading NULL as "admit anyway".
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(job.getWorkspace().getId());
        Map<String, PracticeReviewTier> tierBySlug = new HashMap<>();
        for (Practice practice : practiceRepository.findByWorkspaceId(job.getWorkspace().getId())) {
            tierBySlug.put(practice.getSlug(), ReviewTierResolver.effectiveTierOf(practice, defaults.defaultTier()));
        }

        List<ValidatedFinding> admitted = new ArrayList<>(findings.size());
        List<ValidatedFinding> withheld = new ArrayList<>();
        for (ValidatedFinding finding : findings) {
            PracticeReviewTier tier = tierBySlug.get(finding.practiceSlug());
            if (tier == null) {
                log.warn(
                    "No tier resolved for a delivered finding's practice, so the tier axis cannot " +
                        "withhold it: slug={}, jobId={}",
                    finding.practiceSlug(),
                    job.getId()
                );
            }
            if (FeedbackAdmission.delivers(origin, tier, defaults.reach(), FeedbackChannel.IN_CONTEXT)) {
                admitted.add(finding);
            } else {
                withheld.add(finding);
            }
        }
        if (withheld.isEmpty()) {
            return findings;
        }
        log.info(
            "Autonomy tier withheld {} of {} finding(s) from the artifact: jobId={}",
            withheld.size(),
            findings.size(),
            job.getId()
        );
        recordWithheld(job, withheld, FeedbackSuppressionReason.PRACTICE_TIER_QUIET);
        return admitted;
    }

    private void recordWithheld(AgentJob job, List<ValidatedFinding> withheld, FeedbackSuppressionReason reason) {
        Map<String, Observation> byOccurrence = new HashMap<>();
        for (Observation observation : observationRepository.findByAgentJobId(job.getId())) {
            byOccurrence.put(observation.getOccurrenceKey(), observation);
        }
        int index = 0;
        for (ValidatedFinding finding : withheld) {
            // Overflowing the band would address the NEXT band's unit, which the (agent_job_id, position)
            // guard would then read as "already recorded" and drop.
            if (index >= FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH) {
                log.warn(
                    "Withheld-feedback ledger band full at {} rows; remaining withheld findings are unrecorded: jobId={}",
                    index,
                    job.getId()
                );
                return;
            }
            String occurrenceKey = finding.occurrenceKey();
            if (occurrenceKey == null) {
                continue; // never persisted, so there is no observation for a ledger row to bind
            }
            Observation observation = byOccurrence.get(occurrenceKey);
            if (observation == null) {
                continue;
            }
            try {
                feedbackLedgerRecorder.recordWithheld(job, observation, reason, index++);
            } catch (RuntimeException e) {
                log.warn("Withheld-feedback ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
            }
        }
    }
}
