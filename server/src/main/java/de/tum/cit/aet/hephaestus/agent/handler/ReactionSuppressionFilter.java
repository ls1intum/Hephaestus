package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionAction;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reaction-aware re-nag suppression (ADR 0021, B2). Runs AFTER the findings are persisted (so each carries
 * its stable {@code recurrence_key}) and BEFORE the summary/inline notes are composed: a locus the student
 * already DISPUTED / marked NOT_APPLICABLE on an EARLIER run is dropped from this run's delivery (and a
 * SUPPRESSED ledger row is written so an eval sees it was deliberately withheld, not missed). A locus the
 * student marked ADDRESSED ("I fixed it") but that is STILL assessed BAD this run is kept, with stiffer wording.
 *
 * <p>The reaction is captured against the EPHEMERAL per-run finding id, which differs every run; matching is
 * therefore by {@code recurrence_key} (A2 denormalized it onto the reaction). Flag-gated
 * ({@code hephaestus.practice-review.reaction-suppression}); a no-op when off, when no findings were persisted,
 * or when no reaction matches.
 */
@Component
class ReactionSuppressionFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactionSuppressionFilter.class);

    private static final Set<ReactionAction> SUPPRESS_ACTIONS = Set.of(
        ReactionAction.DISPUTED,
        ReactionAction.NOT_APPLICABLE
    );

    // Credential-leak practices whose still-BAD findings a reaction may never silence: a single DISPUTED /
    // NOT_APPLICABLE must not permanently mute a secret that is STILL present this run. Membership here ==
    // never-silenceable — a slug rename or a second secret practice must be added here (caught in one place)
    // rather than silently becoming reaction-silenceable.
    private static final Set<String> UNSUPPRESSABLE_SECRET_SLUGS = Set.of("hardcoded-secrets");

    private final ObservationRepository observationRepository;
    private final ReactionRepository reactionRepository;
    private final FeedbackLedgerRecorder feedbackLedgerRecorder;
    private final PracticeReviewProperties reviewProperties;

    ReactionSuppressionFilter(
        ObservationRepository observationRepository,
        ReactionRepository reactionRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        PracticeReviewProperties reviewProperties
    ) {
        this.observationRepository = observationRepository;
        this.reactionRepository = reactionRepository;
        this.feedbackLedgerRecorder = feedbackLedgerRecorder;
        this.reviewProperties = reviewProperties;
    }

    /** Which findings to still deliver (escalated ones already rewritten) and how many were suppressed. */
    record ReactionDecision(List<ValidatedFinding> deliverable, int suppressedCount) {}

    // Read-only tx: we run outside the handler's transaction and read scalar identity columns off the
    // persisted observations. recordSuppressed writes in its own REQUIRES_NEW tx, so readOnly does not bind it.
    @Transactional(readOnly = true)
    public ReactionDecision evaluate(AgentJob job, List<ValidatedFinding> scopedFindings) {
        if (!reviewProperties.reactionSuppression()) {
            return new ReactionDecision(scopedFindings, 0);
        }
        List<Observation> persisted = observationRepository.findByAgentJobId(job.getId());
        if (persisted.isEmpty()) {
            return new ReactionDecision(scopedFindings, 0);
        }

        // All observations of one job share the recipient + target. The reacting party is the subject
        // (== the developer for the author-side catalogue today) — the same aboutUserId deliver() folded
        // into each recurrence_key. about_user_id is always populated, so no fallback is needed.
        Observation any = persisted.get(0);
        long aboutUserId = any.getAboutUserId();

        Map<String, Observation> persistedByKey = new HashMap<>();
        for (Observation f : persisted) {
            if (f.getRecurrenceKey() != null) {
                persistedByKey.putIfAbsent(f.getRecurrenceKey(), f);
            }
        }
        // Every persisted observation may carry a null recurrence_key (pre-C2 rows / a detector that emitted
        // no locatable findings). The reaction lookup is a native query whose `IN (:recurrenceKeys)` would
        // render as `IN ()` and crash on Postgres — short-circuit with no suppression when there are no keys.
        if (persistedByKey.isEmpty()) {
            return new ReactionDecision(scopedFindings, 0);
        }
        Map<String, ReactionAction> actionByKey = new HashMap<>();
        for (Reaction r : reactionRepository.findLatestByRecurrenceKeysAndReactor(
            persistedByKey.keySet(),
            aboutUserId
        )) {
            actionByKey.put(r.getRecurrenceKey(), r.getAction());
        }
        if (actionByKey.isEmpty()) {
            return new ReactionDecision(scopedFindings, 0);
        }

        List<ValidatedFinding> deliverable = new ArrayList<>(scopedFindings.size());
        int suppressed = 0;
        int suppressedIndex = 0;
        for (ValidatedFinding vf : scopedFindings) {
            // Use the recurrence_key the handler already stamped from the value deliver() persisted (it runs
            // strictly after that stamp loop), so the match is provably identical to the persisted row a
            // reaction is keyed to — not a parallel recompute that could drift. A finding with no stamped key
            // was never persisted (unknown slug / no locatable findings), so no reaction can target it.
            String key = vf.recurrenceKey();
            if (key == null) {
                deliverable.add(vf);
                continue;
            }
            ReactionAction action = actionByKey.get(key);
            // A live credential-leak BAD alarm is never silenceable by a reaction (see UNSUPPRESSABLE_SECRET_SLUGS).
            boolean unsuppressableSecret =
                UNSUPPRESSABLE_SECRET_SLUGS.contains(vf.practiceSlug()) && vf.assessment() == Assessment.BAD;
            if (!unsuppressableSecret && action != null && SUPPRESS_ACTIONS.contains(action)) {
                Observation pf = persistedByKey.get(key);
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
            if (action == ReactionAction.ADDRESSED && vf.assessment() == Assessment.BAD) {
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
            vf.presence(),
            vf.assessment(),
            vf.severity(),
            vf.confidence(),
            vf.evidence(),
            reasoning,
            vf.guidance(),
            vf.suggestedDiffNotes(),
            vf.recurrenceKey()
        );
    }

    private static FeedbackSuppressionReason reasonFor(ReactionAction action) {
        return action == ReactionAction.DISPUTED
            ? FeedbackSuppressionReason.REACTED_DISPUTED
            : FeedbackSuppressionReason.REACTED_NOT_APPLICABLE;
    }
}
