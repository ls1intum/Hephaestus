package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies each practice's loudness tier to the in-context channel: a finding reaches the artifact only if
 * its practice is at {@link PracticeReviewTier#ENGAGE}.
 *
 * <p>Runs strictly AFTER the findings are persisted and stamped with their observation keys, which is the
 * whole point of the tier — {@code MEASURE} and {@code COACH} are measured and recorded exactly like
 * {@code ENGAGE}, and differ only in how far the result travels. Nothing here can affect the behaviour
 * time series; it only decides what is said.
 *
 * <p>Each withheld finding gets a SUPPRESSED ledger row ({@code PRACTICE_TIER_QUIET}) rather than being
 * dropped in silence, so a later evaluation can tell a deliberate quiet from a detection miss. Writing
 * the row is best-effort: a ledger failure never blocks the delivery of the findings that survived.
 *
 * <p><strong>An unrecognised practice slug is kept.</strong> A finding whose slug is not in the
 * workspace's catalogue was never persisted as an observation either, so there is no tier to consult and
 * no row to write; withholding it would silently drop feedback on the strength of a lookup miss.
 */
@Component
class PracticeTierGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeTierGate.class);

    private final PracticeRepository practiceRepository;
    private final ObservationRepository observationRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;

    PracticeTierGate(
        PracticeRepository practiceRepository,
        ObservationRepository observationRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder
    ) {
        this.practiceRepository = practiceRepository;
        this.observationRepository = observationRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
    }

    /**
     * The subset of {@code findings} whose practice tier admits the in-context channel, in the order given.
     *
     * <p>Returns {@code findings} unchanged when the job has no workspace or every practice is at
     * {@code ENGAGE} — the overwhelmingly common case, which costs one catalogue read and no writes.
     */
    @Transactional(readOnly = true)
    List<ValidatedFinding> admitInContext(AgentJob job, List<ValidatedFinding> findings) {
        if (findings.isEmpty() || job.getWorkspace() == null || job.getWorkspace().getId() == null) {
            return findings;
        }
        Map<String, PracticeReviewTier> tierBySlug = new HashMap<>();
        for (Practice practice : practiceRepository.findByWorkspaceId(job.getWorkspace().getId())) {
            tierBySlug.put(practice.getSlug(), practice.getReviewTier());
        }

        List<ValidatedFinding> admitted = new ArrayList<>(findings.size());
        List<ValidatedFinding> withheld = new ArrayList<>();
        for (ValidatedFinding finding : findings) {
            PracticeReviewTier tier = tierBySlug.get(finding.practiceSlug());
            if (tier == null || tier.delivers(FeedbackChannel.IN_CONTEXT)) {
                admitted.add(finding);
            } else {
                withheld.add(finding);
            }
        }
        if (withheld.isEmpty()) {
            return findings;
        }
        log.info(
            "Loudness tier withheld {} of {} finding(s) from the artifact: jobId={}",
            withheld.size(),
            findings.size(),
            job.getId()
        );
        recordWithheld(job, withheld);
        return admitted;
    }

    private void recordWithheld(AgentJob job, List<ValidatedFinding> withheld) {
        Map<String, Observation> byOccurrence = new HashMap<>();
        for (Observation observation : observationRepository.findByAgentJobId(job.getId())) {
            byOccurrence.put(observation.getOccurrenceKey(), observation);
        }
        int index = 0;
        for (ValidatedFinding finding : withheld) {
            // The band is one thousand slots wide and overflowing it would address the NEXT band's unit,
            // which the (agent_job_id, position) guard would then read as "already recorded" and drop.
            if (index >= FeedbackLedgerRecorder.UNIT_ORDINAL_BAND_WIDTH) {
                log.warn(
                    "Tier-withheld ledger band full at {} rows; remaining withheld findings are unrecorded: jobId={}",
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
                feedbackLedgerRecorder.recordTierWithheld(job, observation, index++);
            } catch (RuntimeException e) {
                log.warn("Tier-withheld ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
            }
        }
    }
}
