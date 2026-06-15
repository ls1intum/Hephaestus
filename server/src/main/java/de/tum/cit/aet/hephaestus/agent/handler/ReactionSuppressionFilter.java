package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReaction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionAction;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reaction-aware re-nag suppression (ADR 0021, B2). Runs AFTER the findings are persisted (so each carries
 * its stable {@code correlation_key}) and BEFORE the summary/inline notes are composed: a locus the student
 * already DISPUTED / marked NOT_APPLICABLE / DISMISSED on an EARLIER run is dropped from this run's delivery
 * (and a SUPPRESSED ledger row is written so an eval sees it was deliberately withheld, not missed). A locus
 * the student marked APPLIED ("I fixed it") but that is STILL NOT_OBSERVED this run is kept and flagged for
 * stiffer wording.
 *
 * <p>The reaction is captured against the EPHEMERAL per-run finding id, which differs every run; matching is
 * therefore by {@code correlation_key} (A2 denormalized it onto the reaction). Flag-gated
 * ({@code hephaestus.practice-review.reaction-suppression}); a no-op returning all findings when off, when no
 * findings were persisted, or when no reaction matches.
 */
@Component
class ReactionSuppressionFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactionSuppressionFilter.class);

    /** Reactions that mean "stop showing me this" → suppress on re-detection. */
    private static final Set<FindingReactionAction> SUPPRESS_ACTIONS = Set.of(
        FindingReactionAction.DISPUTED,
        FindingReactionAction.NOT_APPLICABLE
    );

    private final PracticeFindingRepositoryRef findingRepo;
    private final FindingReactionRepository findingReactionRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final PracticeReviewProperties reviewProperties;

    ReactionSuppressionFilter(
        de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository practiceFindingRepository,
        FindingReactionRepository findingReactionRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        PracticeReviewProperties reviewProperties
    ) {
        this.findingRepo = practiceFindingRepository::findByAgentJobId;
        this.findingReactionRepository = findingReactionRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.reviewProperties = reviewProperties;
    }

    /** Narrow seam over the repository so the matching logic is unit-testable without a JPA mock graph. */
    @FunctionalInterface
    interface PracticeFindingRepositoryRef {
        List<PracticeFinding> findByAgentJobId(java.util.UUID agentJobId);
    }

    /** The outcome: which findings to still deliver, which loci to escalate wording on, and how many were suppressed. */
    record ReactionDecision(List<ValidatedFinding> deliverable, Set<EscalatedLocus> escalatedLoci, int suppressedCount) {}

    /** A locus the student said was fixed but that recurs — matched in the composer by (slug, first path). */
    record EscalatedLocus(String practiceSlug, @Nullable String firstLocationPath) {}

    // Read-only tx so the persisted findings' lazy Practice/contributor associations initialise while we read
    // their slug + subject (we run outside the handler's transaction). recordSuppressed writes in its own
    // REQUIRES_NEW tx, so this readOnly flag does not constrain it.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ReactionDecision evaluate(AgentJob job, List<ValidatedFinding> scopedFindings) {
        if (!reviewProperties.reactionSuppression()) {
            return new ReactionDecision(scopedFindings, Set.of(), 0);
        }
        List<PracticeFinding> persisted = findingRepo.findByAgentJobId(job.getId());
        if (persisted.isEmpty()) {
            return new ReactionDecision(scopedFindings, Set.of(), 0);
        }

        // The reacting party is the finding's subject (== contributor today; subject_user_id is always null
        // for the author-side catalogue). All findings of one job share the same recipient.
        PracticeFinding any = persisted.get(0);
        Long aboutUserId = any.getSubjectUserId() != null ? any.getSubjectUserId() : any.getContributor().getId();

        // Map each persisted finding's stable LOCUS identity (slug, first path, title) → its correlation_key.
        Map<LocusId, String> keyByLocus = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (PracticeFinding f : persisted) {
            if (f.getCorrelationKey() == null) {
                continue;
            }
            keyByLocus.put(
                new LocusId(f.getPractice().getSlug(), firstLocationPath(f.getEvidence()), f.getTitle()),
                f.getCorrelationKey()
            );
            keys.add(f.getCorrelationKey());
        }
        if (keys.isEmpty()) {
            return new ReactionDecision(scopedFindings, Set.of(), 0);
        }

        Map<String, FindingReactionAction> actionByKey = new HashMap<>();
        for (FindingReaction r : findingReactionRepository.findLatestByCorrelationKeysAndContributor(keys, aboutUserId)) {
            if (r.getCorrelationKey() != null) {
                actionByKey.put(r.getCorrelationKey(), r.getAction());
            }
        }
        if (actionByKey.isEmpty()) {
            return new ReactionDecision(scopedFindings, Set.of(), 0);
        }

        List<ValidatedFinding> deliverable = new ArrayList<>(scopedFindings.size());
        Set<EscalatedLocus> escalated = new HashSet<>();
        Map<String, PracticeFinding> persistedByKey = new HashMap<>();
        for (PracticeFinding f : persisted) {
            if (f.getCorrelationKey() != null) {
                persistedByKey.putIfAbsent(f.getCorrelationKey(), f);
            }
        }
        int suppressed = 0;
        int suppressedIndex = 0;
        for (ValidatedFinding vf : scopedFindings) {
            String path = firstLocationPath(vf.evidence());
            String key = keyByLocus.get(new LocusId(vf.practiceSlug(), path, vf.title()));
            FindingReactionAction action = key == null ? null : actionByKey.get(key);
            if (action != null && SUPPRESS_ACTIONS.contains(action)) {
                // v1: never re-nag a disputed/NA/dismissed locus (materially-changed escape is a follow-up).
                PracticeFinding pf = persistedByKey.get(key);
                if (pf != null) {
                    try {
                        feedbackLedgerRecorder.recordSuppressed(job, pf, reasonFor(action), suppressedIndex++);
                    } catch (RuntimeException e) {
                        log.warn("Suppressed-ledger write failed (delivery unaffected): jobId={}", job.getId(), e);
                    }
                }
                suppressed++;
                continue; // drop from delivery
            }
            if (action == FindingReactionAction.APPLIED && vf.verdict() == Verdict.NOT_OBSERVED) {
                // Student said "fixed", but it recurs — keep it, with a stiffer opener (B2 escalation).
                escalated.add(new EscalatedLocus(vf.practiceSlug(), path));
                deliverable.add(withEscalatedReasoning(vf));
                continue;
            }
            deliverable.add(vf);
        }
        if (suppressed > 0 || !escalated.isEmpty()) {
            log.info(
                "Reaction-aware filter: jobId={}, suppressed={}, escalated={}, delivered={}/{}",
                job.getId(),
                suppressed,
                escalated.size(),
                deliverable.size(),
                scopedFindings.size()
            );
        }
        return new ReactionDecision(deliverable, escalated, suppressed);
    }

    /** A copy of the finding with a stiffer opener, for a locus the student said was fixed but that recurs. */
    private static ValidatedFinding withEscalatedReasoning(ValidatedFinding vf) {
        String prefix = "You previously marked this as fixed, but it is still present. ";
        String reasoning = vf.reasoning() == null || vf.reasoning().isBlank() ? prefix.trim() : prefix + vf.reasoning();
        return new ValidatedFinding(
            vf.practiceSlug(),
            vf.title(),
            vf.verdict(),
            vf.severity(),
            vf.confidence(),
            vf.evidence(),
            reasoning,
            vf.guidance(),
            vf.suggestedDiffNotes()
        );
    }

    private static FeedbackSuppressionReason reasonFor(FindingReactionAction action) {
        return action == FindingReactionAction.DISPUTED
            ? FeedbackSuppressionReason.REACTED_DISPUTED
            : FeedbackSuppressionReason.REACTED_NOT_APPLICABLE;
    }

    /** First evidence file path — the SAME rule deliver() used to derive each finding's correlation_key. */
    @Nullable
    private static String firstLocationPath(@Nullable JsonNode evidence) {
        return PracticeDetectionDeliveryService.firstLocationPath(evidence);
    }

    private record LocusId(String practiceSlug, @Nullable String firstLocationPath, String title) {}
}
