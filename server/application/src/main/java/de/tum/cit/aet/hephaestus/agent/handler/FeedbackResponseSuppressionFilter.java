package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository.ResultResolutionProjection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Holds back feedback about results the developer disputed or called not applicable. */
@Component
class FeedbackResponseSuppressionFilter {

    private static final Logger log = LoggerFactory.getLogger(FeedbackResponseSuppressionFilter.class);

    private static final Set<FeedbackResolution> SUPPRESS_ACTIONS =
            Set.of(FeedbackResolution.DISPUTED, FeedbackResolution.NOT_APPLICABLE);

    private final PracticeResultRepository practiceResultRepository;
    private final ObservationRepository observationRepository;
    private final ReactionRepository reactionRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;

    FeedbackResponseSuppressionFilter(
            PracticeResultRepository practiceResultRepository,
            ObservationRepository observationRepository,
            ReactionRepository reactionRepository,
            FeedbackLedgerRecorder feedbackLedgerRecorder) {
        this.practiceResultRepository = practiceResultRepository;
        this.observationRepository = observationRepository;
        this.reactionRepository = reactionRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
    }

    record SuppressionDecision(List<ValidatedResult> deliverable, int suppressedCount) {}

    // Read-only tx: we run outside the handler's transaction and read scalar identity columns off the
    // persisted results. recordSuppressed writes in its own REQUIRES_NEW tx, so readOnly does not bind it.
    @Transactional(readOnly = true)
    public SuppressionDecision evaluate(AgentJob job, List<ValidatedResult> scopedResults) {
        return evaluate(job, scopedResults, false);
    }

    /** The same gate with suppressed rows in the caller's acceptance transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SuppressionDecision evaluateInCurrentTransaction(AgentJob job, List<ValidatedResult> scopedResults) {
        return evaluate(job, scopedResults, true);
    }

    private SuppressionDecision evaluate(
            AgentJob job, List<ValidatedResult> scopedResults, boolean inCurrentTransaction) {
        long workspaceId = job.getWorkspace().getId();
        List<PracticeResult> persisted = practiceResultRepository.findByAgentJobId(job.getId(), workspaceId);
        if (persisted.isEmpty()) {
            return new SuppressionDecision(scopedResults, 0);
        }

        Map<String, PracticeResult> persistedByOccurrence = new HashMap<>();
        for (PracticeResult practiceResult : persisted) {
            persistedByOccurrence.put(practiceResult.getOccurrenceKey(), practiceResult);
        }
        // Recurrence is a property of each located problem: a result recurs where one of its problems does.
        Map<UUID, Set<String>> recurrenceByResult = new HashMap<>();
        for (Observation problem : observationRepository.findByPracticeResultIds(
                workspaceId, persisted.stream().map(PracticeResult::getId).toList())) {
            if (problem.getRecurrenceKey() != null) {
                recurrenceByResult
                        .computeIfAbsent(problem.getPracticeResultId(), id -> new HashSet<>())
                        .add(problem.getRecurrenceKey());
            }
        }
        String[] recurrenceKeys = recurrenceByResult.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .toArray(String[]::new);
        List<ResultResolutionProjection> answers = reactionRepository.findCurrentResolutions(
                workspaceId, persisted.stream().map(PracticeResult::getId).toList(), recurrenceKeys);
        if (answers.isEmpty()) {
            return new SuppressionDecision(scopedResults, 0);
        }

        List<ValidatedResult> deliverable = new ArrayList<>(scopedResults.size());
        int suppressed = 0;
        int suppressedIndex = 0;
        for (ValidatedResult vf : scopedResults) {
            PracticeResult pf = persistedByOccurrence.get(vf.occurrenceKey());
            if (pf == null) {
                deliverable.add(vf);
                continue;
            }
            FeedbackResolution action = standingAnswer(
                            pf, recurrenceByResult.getOrDefault(pf.getId(), Set.of()), answers)
                    .map(answer -> FeedbackResolution.valueOf(answer.getResolution()))
                    .orElse(null);
            if (action != null && SUPPRESS_ACTIONS.contains(action)) {
                if (inCurrentTransaction) {
                    // Part of the caller's whole outcome: a failed row fails it rather than leaving it incomplete.
                    feedbackLedgerRecorder.recordSuppressedInCurrentTransaction(
                            job, pf, reasonFor(action), suppressedIndex++);
                    suppressed++;
                    continue;
                }
                try {
                    feedbackLedgerRecorder.recordSuppressed(job, pf, reasonFor(action), suppressedIndex++);
                } catch (RuntimeException e) {
                    log.warn("Suppressed-ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
                }
                suppressed++;
                continue;
            }
            deliverable.add(vf);
        }
        if (suppressed > 0) {
            log.info(
                    "Feedback-response filter: jobId={}, suppressed={}, delivered={}/{}",
                    job.getId(),
                    suppressed,
                    deliverable.size(),
                    scopedResults.size());
        }
        return new SuppressionDecision(deliverable, suppressed);
    }

    /** The newest answer that speaks for {@code practiceResult}: on its own feedback, or on the same claim earlier. */
    private static Optional<ResultResolutionProjection> standingAnswer(
            PracticeResult practiceResult, Set<String> recurrenceKeys, List<ResultResolutionProjection> answers) {
        return answers.stream()
                .filter(answer -> answer.getPracticeResultId().equals(practiceResult.getId())
                        || sameClaimInAnEarlierReview(practiceResult, recurrenceKeys, answer))
                .max(Comparator.comparing(ResultResolutionProjection::getRespondedAt));
    }

    private static boolean sameClaimInAnEarlierReview(
            PracticeResult practiceResult, Set<String> recurrenceKeys, ResultResolutionProjection answer) {
        UUID jobId = practiceResult.getAgentJobId();
        return !answer.getAgentJobId().equals(jobId)
                && answer.getRecurrenceKey() != null
                && recurrenceKeys.contains(answer.getRecurrenceKey())
                && practiceResult.getOutcome().name().equals(answer.getOutcome())
                && normalizeClaim(practiceResult.getSummary()).equals(normalizeClaim(answer.getSummary()));
    }

    /** A location and outcome alone can describe different claims; carry a response only for the same statement. */
    private static String normalizeClaim(String summary) {
        return summary.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static FeedbackSuppressionReason reasonFor(FeedbackResolution action) {
        return action == FeedbackResolution.DISPUTED
                ? FeedbackSuppressionReason.REACTED_DISPUTED
                : FeedbackSuppressionReason.REACTED_NOT_APPLICABLE;
    }
}
