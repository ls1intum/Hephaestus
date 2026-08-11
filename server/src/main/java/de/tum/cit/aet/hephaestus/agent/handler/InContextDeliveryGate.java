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
 * <p>Runs strictly AFTER the findings are persisted and stamped with their observation keys, which is
 * the whole point of both rules — a {@code PROPOSE} practice and a backfill are measured and
 * recorded exactly like an engaged live run, and differ only in how far the result travels. Nothing here
 * can affect the behaviour time series; it only decides what is said.
 *
 * <p>The provenance rule is what keeps a backfill campaign from commenting on merged pull requests:
 * every subscriber to a months-old pull request would be notified about work nobody can act on. It is
 * checked once for the whole job rather than per finding, because a job has exactly one origin.
 *
 * <p>Each withheld finding gets a SUPPRESSED ledger row — {@code PRACTICE_TIER_QUIET} or
 * {@code BACKFILL_QUIET}, whichever rule fired — rather than being dropped in silence, so a later
 * evaluation can tell a deliberate quiet from a detection miss. Writing the row is best-effort: a ledger
 * failure never blocks the delivery of the findings that survived.
 *
 * <p><strong>An unrecognised practice slug is kept</strong> when only the tier would have withheld it. A
 * finding whose slug is not in the workspace's catalogue was never persisted as an observation either,
 * so there is no tier to consult and no row to write; withholding it would silently drop feedback on the
 * strength of a lookup miss. The provenance rule has no such escape hatch — it needs no lookup.
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

    /**
     * The subset of {@code findings} that may be placed on the artifact, in the order given.
     *
     * <p>Returns {@code findings} unchanged when the job has no workspace or everything is admitted —
     * the overwhelmingly common case, which costs one catalogue read and no writes.
     */
    @Transactional(readOnly = true)
    List<ValidatedFinding> admitInContext(AgentJob job, List<ValidatedFinding> findings) {
        if (findings.isEmpty() || job.getWorkspace() == null || job.getWorkspace().getId() == null) {
            return findings;
        }
        ObservationOrigin origin = PracticeDetectionDeliveryService.originOf(job.getMetadata());
        if (!origin.delivers(FeedbackChannel.IN_CONTEXT)) {
            // One decision for the whole job: a run has a single provenance, and no per-practice dial can
            // make a retrospective finding actionable on the artifact it is about.
            log.info(
                "Provenance withheld all {} finding(s) from the artifact: origin={}, jobId={}",
                findings.size(),
                origin,
                job.getId()
            );
            recordWithheld(job, findings, FeedbackSuppressionReason.BACKFILL_QUIET);
            return List.of();
        }

        // Effective tiers, resolved through practice -> area -> workspace. The raw column is not the
        // answer: a practice that holds no opinion of its own inherits one, and treating its NULL as an
        // unresolved lookup would admit it whatever its area or its workspace had decided.
        //
        // Resolved from the workspace ID rather than off job.getWorkspace(). The job reaches this gate
        // detached on some paths, so reading a lazy association here would make the delivery rule's
        // correctness depend on whether the caller happens to hold a session — which is exactly the trap
        // the conversational router's tier projection was written to avoid.
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(job.getWorkspace().getId());
        Map<String, PracticeReviewTier> tierBySlug = new HashMap<>();
        for (Practice practice : practiceRepository.findByWorkspaceId(job.getWorkspace().getId())) {
            tierBySlug.put(practice.getSlug(), ReviewTierResolver.effectiveTierOf(practice, defaults.defaultTier()));
        }

        List<ValidatedFinding> admitted = new ArrayList<>(findings.size());
        List<ValidatedFinding> withheld = new ArrayList<>();
        for (ValidatedFinding finding : findings) {
            if (
                FeedbackAdmission.delivers(
                    origin,
                    tierBySlug.get(finding.practiceSlug()),
                    defaults.reach(),
                    FeedbackChannel.IN_CONTEXT
                )
            ) {
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
