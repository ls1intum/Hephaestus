package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.practices.PracticeDeliveryBehavior;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The typed shapes delivery reads admitted results in: {@link ValidatedResult} with its {@link ValidatedProblem}s, and
 * the delivery content composed from them. Candidates are read by {@code CandidateParser}; nothing here parses runner
 * output.
 */
public final class ReviewResultParser {

    public static final int MAX_SUMMARY_LENGTH = 160;

    static final int MAX_MR_NOTE_LENGTH = 60_000;

    /** The line notes one review may carry; the composition contract owns the number. */
    static final int MAX_DELIVERY_DIFF_NOTES = ComposedReview.MAX_INLINE_NOTES;

    private ReviewResultParser() {}

    /**
     * One located problem of an admitted result.
     *
     * @param observationId the stored row's id; null while admission builds it, before the row exists
     * @param citations the verified citations, as stored
     * @param basis why the problem belongs to this work; null for a problem recorded before the basis was kept
     */
    public record ValidatedProblem(
            @Nullable UUID observationId,
            int position,
            @Nullable String observationKey,
            Severity severity,
            String summary,
            String explanation,
            JsonNode citations,
            @Nullable JsonNode basis,
            @Nullable String nextStep,
            @Nullable String recurrenceKey) {

        public ValidatedProblem withId(UUID id) {
            return new ValidatedProblem(
                    id,
                    position,
                    observationKey,
                    severity,
                    summary,
                    explanation,
                    citations,
                    basis,
                    nextStep,
                    recurrenceKey);
        }

        public static ValidatedProblem of(Observation observation) {
            return new ValidatedProblem(
                    observation.getId(),
                    observation.getPosition(),
                    observation.getObservationKey(),
                    observation.getSeverity(),
                    observation.getSummary(),
                    observation.getExplanation(),
                    observation.getCitations(),
                    observation.getBasis(),
                    observation.getNextStep(),
                    observation.getRecurrenceKey());
        }
    }

    /**
     * One admitted result as delivery reads it.
     *
     * @param evidence the result's own evidence: its citations and the warrants of its outcome
     * @param evidenceRationale the result's explanation
     * @param keys the identities {@code ReviewOutputService} persisted for this result, stamped by the handler rather
     *     than recomputed downstream so they cannot drift from the stored row; {@code null} until stamped
     * @param problems the result's located problems, in display order; empty unless it is {@code NOT_MET}
     * @param example one place of the work that shows the practice done well; null when there is none
     */
    public record ValidatedResult(
            String practiceSlug,
            String summary,
            Outcome outcome,
            @Nullable Severity severity,
            @Nullable JsonNode evidence,
            @Nullable String evidenceRationale,
            @Nullable ResultKeys keys,
            PracticeDeliveryBehavior deliveryBehavior,
            List<ValidatedProblem> problems,
            @Nullable JsonNode example) {
        public ValidatedResult {
            problems = List.copyOf(problems);
        }

        public ValidatedResult(
                String practiceSlug,
                String summary,
                Outcome outcome,
                @Nullable Severity severity,
                @Nullable JsonNode evidence,
                @Nullable String evidenceRationale,
                @Nullable ResultKeys keys,
                PracticeDeliveryBehavior deliveryBehavior) {
            this(
                    practiceSlug,
                    summary,
                    outcome,
                    severity,
                    evidence,
                    evidenceRationale,
                    keys,
                    deliveryBehavior,
                    List.of(),
                    null);
        }

        public ValidatedResult(
                String practiceSlug,
                String summary,
                Outcome outcome,
                @Nullable Severity severity,
                @Nullable JsonNode evidence,
                @Nullable String evidenceRationale,
                @Nullable ResultKeys keys) {
            this(
                    practiceSlug,
                    summary,
                    outcome,
                    severity,
                    evidence,
                    evidenceRationale,
                    keys,
                    PracticeDeliveryBehavior.DEFAULT);
        }

        /** A result not yet stamped with its persisted identities. */
        public ValidatedResult(
                String practiceSlug,
                String summary,
                Outcome outcome,
                @Nullable Severity severity,
                @Nullable JsonNode evidence,
                @Nullable String evidenceRationale) {
            this(practiceSlug, summary, outcome, severity, evidence, evidenceRationale, null);
        }

        /** A stored result with its stored problems. */
        public static ValidatedResult of(PracticeResult result, List<Observation> problems) {
            return new ValidatedResult(
                    result.getPractice().getSlug(),
                    result.getSummary(),
                    result.getOutcome(),
                    result.getSeverity(),
                    result.getEvidence(),
                    result.getEvidenceRationale(),
                    new ResultKeys(result.getOccurrenceKey(), result.getId()),
                    result.getPracticeRevision() == null
                            ? result.getPractice().getDeliveryBehavior()
                            : result.getPracticeRevision().getDeliveryBehavior(),
                    problems.stream().map(ValidatedProblem::of).toList(),
                    result.getExample());
        }

        public ValidatedResult withKeys(@Nullable ResultKeys keys) {
            return new ValidatedResult(
                    practiceSlug,
                    summary,
                    outcome,
                    severity,
                    evidence,
                    evidenceRationale,
                    keys,
                    deliveryBehavior,
                    problems,
                    example);
        }

        public @Nullable String occurrenceKey() {
            return keys == null ? null : keys.occurrenceKey();
        }

        public @Nullable UUID practiceResultId() {
            return keys == null ? null : keys.id();
        }

        /** The problem with this id, if this result has it. */
        public Optional<ValidatedProblem> problem(UUID observationId) {
            return problems.stream()
                    .filter(problem -> observationId.equals(problem.observationId()))
                    .findFirst();
        }
    }

    public record DiscardedEntry(int index, String reason) {}

    /**
     * Pre-rendered delivery content from the agent, alongside the structured results — the server sanitizes
     * and posts it without further rendering.
     *
     * @param withheld the results the composer chose not to render, for the ledger to record as SUPPRESSED
     * @param summaryContributors occurrence keys of the results the summary note was written from; each line
     *     note carries its own. Null only on a dispatch package persisted before they were recorded
     * @param inlineMarker the marker every inline copy of this package carries, sealed with it; null on a package
     *     persisted before it was recorded, whose copies keep the historical marker and readback
     * @param summaryObservations ids of the problems the summary note names; null on a package persisted before
     *     problems were recorded, whose evidence is bound per result
     */
    public record DeliveryContent(
            @Nullable String mrNote,
            List<DiffNote> diffNotes,
            List<WithheldResult> withheld,
            @Nullable List<String> summaryContributors,
            @Nullable String inlineMarker,
            @Nullable List<String> summaryObservations) {
        public DeliveryContent(
                @Nullable String mrNote,
                List<DiffNote> diffNotes,
                List<WithheldResult> withheld,
                @Nullable List<String> summaryContributors,
                @Nullable String inlineMarker) {
            this(mrNote, diffNotes, withheld, summaryContributors, inlineMarker, null);
        }

        public DeliveryContent(
                @Nullable String mrNote,
                List<DiffNote> diffNotes,
                List<WithheldResult> withheld,
                @Nullable List<String> summaryContributors) {
            this(mrNote, diffNotes, withheld, summaryContributors, null);
        }

        public DeliveryContent withDiffNotes(List<DiffNote> notes) {
            return new DeliveryContent(mrNote, notes, withheld, summaryContributors, inlineMarker, summaryObservations);
        }

        /** The same decisions with nothing to place on the work. */
        public DeliveryContent withoutNote() {
            return new DeliveryContent(null, List.of(), withheld, List.of(), inlineMarker, List.of());
        }

        /** Every result some part of the content was written from; null on a pre-upgrade package. */
        public @Nullable List<String> contributors() {
            if (summaryContributors == null) return null;
            List<String> keys = new ArrayList<>(summaryContributors);
            for (DiffNote note : diffNotes) {
                List<String> noteKeys = note.contributors();
                if (noteKeys != null) keys.addAll(noteKeys);
            }
            return keys.stream().distinct().toList();
        }

        /** Whether the content was written from any of these results. */
        public boolean writtenFromAny(List<ValidatedResult> practiceResults) {
            List<String> keys = contributors();
            return keys != null
                    && practiceResults.stream()
                            .map(ValidatedResult::occurrenceKey)
                            .anyMatch(keys::contains);
        }

        /** The practices of the results the content was written from, which delivery policy checks. */
        public Set<String> contributingPracticeSlugs(List<ValidatedResult> practiceResults) {
            List<String> contributors = contributors();
            List<String> keys = contributors == null ? List.of() : contributors;
            return practiceResults.stream()
                    .filter(practiceResult -> keys.contains(practiceResult.occurrenceKey()))
                    .map(ValidatedResult::practiceSlug)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * A result the review withheld from the rendered delivery, identified by the
     * {@code occurrenceKey} of the result it was persisted as — a per-practiceResult identity, so a
     * withheld result is never confused with another at the same locus.
     */
    public record WithheldResult(String occurrenceKey, FeedbackSuppressionReason reason) {}

    /**
     * An inline diff note targeting a specific file and line range.
     *
     * @param filePath path relative to repo root (new path, not old)
     * @param endLine  optional last line number for multi-line (GitHub only; GitLab ignores)
     * @param deliveryKey opaque receipt-correlation key for this exact result, carried from its
     *     occurrence identity by {@link ComposedReviewAdmission}; null before server-side correlation.
     * @param contributors occurrence keys of every result this note's text was written from: its own, and
     *     any other the composed unit cites. Null before server-side correlation and on a pre-upgrade package
     * @param observations ids of the problems the note names, the anchored one first; null on a package persisted
     *     before problems were recorded
     */
    public record DiffNote(
            String filePath,
            int startLine,
            @Nullable Integer endLine,
            String body,
            @Nullable String deliveryKey,
            @Nullable List<String> contributors,
            @Nullable List<String> observations) {
        public DiffNote(
                String filePath,
                int startLine,
                @Nullable Integer endLine,
                String body,
                @Nullable String deliveryKey,
                @Nullable List<String> contributors) {
            this(filePath, startLine, endLine, body, deliveryKey, contributors, null);
        }

        /** The parser's pre-correlation output shape: a note with no correlation key yet. */
        public DiffNote(String filePath, int startLine, @Nullable Integer endLine, String body) {
            this(filePath, startLine, endLine, body, null, null);
        }
    }
}
