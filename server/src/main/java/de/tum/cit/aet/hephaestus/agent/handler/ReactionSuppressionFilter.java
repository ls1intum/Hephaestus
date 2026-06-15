package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.finding.CorrelationKey;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReaction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Reaction-aware re-nag suppression (ADR 0021, B2). Runs AFTER the findings are persisted (so each carries
 * its stable {@code correlation_key}) and BEFORE the summary/inline notes are composed: a locus the student
 * already DISPUTED / marked NOT_APPLICABLE on an EARLIER run is dropped from this run's delivery (and a
 * SUPPRESSED ledger row is written so an eval sees it was deliberately withheld, not missed). A locus the
 * student marked APPLIED ("I fixed it") but that is STILL NOT_OBSERVED this run is kept, with stiffer wording.
 *
 * <p>The reaction is captured against the EPHEMERAL per-run finding id, which differs every run; matching is
 * therefore by {@code correlation_key} (A2 denormalized it onto the reaction). Flag-gated
 * ({@code hephaestus.practice-review.reaction-suppression}); a no-op when off, when no findings were persisted,
 * or when no reaction matches.
 */
@Component
class ReactionSuppressionFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactionSuppressionFilter.class);

    private static final Set<FindingReactionAction> SUPPRESS_ACTIONS = Set.of(
        FindingReactionAction.DISPUTED,
        FindingReactionAction.NOT_APPLICABLE
    );

    private final PracticeFindingRepository practiceFindingRepository;
    private final FindingReactionRepository findingReactionRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final PracticeReviewProperties reviewProperties;

    ReactionSuppressionFilter(
        PracticeFindingRepository practiceFindingRepository,
        FindingReactionRepository findingReactionRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        PracticeReviewProperties reviewProperties
    ) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.findingReactionRepository = findingReactionRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.reviewProperties = reviewProperties;
    }

    /** Which findings to still deliver (escalated ones already rewritten) and how many were suppressed. */
    record ReactionDecision(List<ValidatedFinding> deliverable, int suppressedCount) {}

    // Read-only tx: we run outside the handler's transaction and call any.getContributor().getId(), which
    // initialises the lazy User proxy (its @Id lives on the BaseGitServiceEntity superclass, so the id is not
    // readable without a load). recordSuppressed writes in its own REQUIRES_NEW tx, so readOnly does not bind it.
    @Transactional(readOnly = true)
    public ReactionDecision evaluate(AgentJob job, List<ValidatedFinding> scopedFindings) {
        if (!reviewProperties.reactionSuppression()) {
            return new ReactionDecision(scopedFindings, 0);
        }
        List<PracticeFinding> persisted = practiceFindingRepository.findByAgentJobId(job.getId());
        if (persisted.isEmpty()) {
            return new ReactionDecision(scopedFindings, 0);
        }

        // All findings of one job share the recipient + target. The reacting party is the subject (== the
        // contributor today; subject_user_id stays null for the author-side catalogue) — the same aboutUserId
        // deliver() folded into each correlation_key.
        PracticeFinding any = persisted.get(0);
        long aboutUserId = any.getSubjectUserId() != null ? any.getSubjectUserId() : any.getContributor().getId();
        String targetType = any.getTargetType().name();
        long targetId = any.getTargetId();

        Map<String, PracticeFinding> persistedByKey = new HashMap<>();
        for (PracticeFinding f : persisted) {
            if (f.getCorrelationKey() != null) {
                persistedByKey.putIfAbsent(f.getCorrelationKey(), f);
            }
        }
        Map<String, FindingReactionAction> actionByKey = new HashMap<>();
        for (FindingReaction r : findingReactionRepository.findLatestByCorrelationKeysAndContributor(
            persistedByKey.keySet(),
            aboutUserId
        )) {
            actionByKey.put(r.getCorrelationKey(), r.getAction());
        }
        if (actionByKey.isEmpty()) {
            return new ReactionDecision(scopedFindings, 0);
        }

        List<ValidatedFinding> deliverable = new ArrayList<>(scopedFindings.size());
        int suppressed = 0;
        int suppressedIndex = 0;
        for (ValidatedFinding vf : scopedFindings) {
            // Recompute the canonical locus key the same way deliver() did, so we match the persisted row a
            // reaction is keyed to — without re-deriving identity (CorrelationKey deliberately excludes the title).
            String key = CorrelationKey.compute(
                vf.practiceSlug(),
                targetType,
                targetId,
                aboutUserId,
                firstLocationPath(vf.evidence())
            );
            FindingReactionAction action = actionByKey.get(key);
            if (action != null && SUPPRESS_ACTIONS.contains(action)) {
                PracticeFinding pf = persistedByKey.get(key);
                if (pf != null) {
                    try {
                        feedbackLedgerRecorder.recordSuppressed(job, pf, reasonFor(action), suppressedIndex++);
                    } catch (RuntimeException e) {
                        log.warn("Suppressed-ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
                    }
                }
                suppressed++;
                continue;
            }
            if (action == FindingReactionAction.APPLIED && vf.verdict() == Verdict.NOT_OBSERVED) {
                deliverable.add(withEscalatedReasoning(vf)); // student said "fixed", but it recurs
                continue;
            }
            deliverable.add(vf);
        }
        if (suppressed > 0) {
            log.info(
                "Reaction-aware filter: jobId={}, suppressed={}, delivered={}/{}",
                job.getId(),
                suppressed,
                deliverable.size(),
                scopedFindings.size()
            );
        }
        return new ReactionDecision(deliverable, suppressed);
    }

    /** A copy of the finding with a stiffer opener, for a locus the student said was fixed but that recurs. */
    private static ValidatedFinding withEscalatedReasoning(ValidatedFinding vf) {
        String prefix = "You previously marked this as fixed, but it is still present. ";
        String reasoning = vf.reasoning() == null || vf.reasoning().isBlank() ? prefix.trim() : prefix + vf.reasoning();
        // Preserve the correlation key the handler stamped on the input so the escalated copy keeps the same
        // cross-run identity as the locus it re-nags.
        return new ValidatedFinding(
            vf.practiceSlug(),
            vf.title(),
            vf.verdict(),
            vf.severity(),
            vf.confidence(),
            vf.evidence(),
            reasoning,
            vf.guidance(),
            vf.suggestedDiffNotes(),
            vf.correlationKey()
        );
    }

    private static FeedbackSuppressionReason reasonFor(FindingReactionAction action) {
        return action == FindingReactionAction.DISPUTED
            ? FeedbackSuppressionReason.REACTED_DISPUTED
            : FeedbackSuppressionReason.REACTED_NOT_APPLICABLE;
    }

    @Nullable
    private static String firstLocationPath(@Nullable JsonNode evidence) {
        return PracticeDetectionDeliveryService.firstLocationPath(evidence);
    }
}
