package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.ReviewRuleFingerprint;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ReviewPracticeOutcomesDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.SlippedPracticeDTO;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What one review run decided about each practice it reviewed, one verdict per practice however many results
 * it recorded. The developer's own review list and the admin review list both read a run through this, so a run
 * never reads "one practice met" on one and "two met" on the other.
 *
 * @param practices the practices counted by verdict
 * @param slipped every practice counted as not met, once each, by name
 */
public record RunPracticeOutcomes(ReviewPracticeOutcomesDTO practices, List<SlippedPracticeDTO> slipped) {

    /**
     * What one result adds to the tally: its practice, its recorded outcome and the fingerprint of the
     * revision it was judged against, null when that revision is gone.
     */
    public record Counted(
            String practiceSlug,
            String practiceName,
            Outcome outcome,
            @Nullable String reviewRuleFingerprint) {
        static Counted of(PracticeResult practiceResult) {
            PracticeRevision evaluated = practiceResult.getPracticeRevision();
            return new Counted(
                    practiceResult.getPractice().getSlug(),
                    practiceResult.getPractice().getName(),
                    practiceResult.getOutcome(),
                    evaluated == null ? null : evaluated.getReviewRuleFingerprint());
        }
    }

    /** @param practiceResults the run's standing results; an invalidated one must already be left out */
    public static RunPracticeOutcomes of(Collection<PracticeResult> practiceResults) {
        return ofCounted(practiceResults.stream().map(Counted::of).toList());
    }

    /** @param results the run's standing results, read narrowly; an invalidated one is left out */
    public static RunPracticeOutcomes ofCounted(Collection<Counted> results) {
        Map<String, String> names = new LinkedHashMap<>();
        results.forEach(result -> names.putIfAbsent(result.practiceSlug(), result.practiceName()));
        Map<String, Verdict> verdicts = results.stream()
                .collect(Collectors.groupingBy(
                        Counted::practiceSlug,
                        Collectors.mapping(
                                RunPracticeOutcomes::countedOutcome,
                                Collectors.collectingAndThen(Collectors.toList(), Verdict::of))));
        return new RunPracticeOutcomes(
                new ReviewPracticeOutcomesDTO(
                        count(verdicts.values(), Verdict.NOT_MET),
                        count(verdicts.values(), Verdict.MET),
                        count(verdicts.values(), Verdict.NOT_APPLICABLE),
                        count(verdicts.values(), Verdict.UNDETERMINED)),
                verdicts.entrySet().stream()
                        .filter(entry -> entry.getValue() == Verdict.NOT_MET)
                        .map(entry -> new SlippedPracticeDTO(
                                entry.getKey(), Objects.requireNonNull(names.get(entry.getKey()))))
                        .sorted(Comparator.comparing(SlippedPracticeDTO::practiceName, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(SlippedPracticeDTO::practiceSlug))
                        .toList());
    }

    /**
     * The outcome a run's summary counts. A met or not-met verdict recorded under an earlier fingerprint
     * scheme, or without one, judged a behavior rather than the whole practice standard, so it counts as
     * undecided; the result itself keeps its recorded outcome.
     */
    private static Outcome countedOutcome(Counted result) {
        String fingerprint = result.reviewRuleFingerprint();
        boolean currentStandard = fingerprint != null && ReviewRuleFingerprint.isCurrentScheme(fingerprint);
        return result.outcome().isDecided() && !currentStandard ? Outcome.UNDETERMINED : result.outcome();
    }

    /** What a run decided about one practice, from every standing result of it. */
    private enum Verdict {
        NOT_MET,
        MET,
        NOT_APPLICABLE,
        UNDETERMINED;

        /** Any problem outweighs any strength. */
        static Verdict of(List<Outcome> outcomes) {
            if (outcomes.stream().anyMatch(Outcome::isNotMet)) {
                return NOT_MET;
            }
            if (outcomes.stream().anyMatch(Outcome::isMet)) {
                return MET;
            }
            return outcomes.stream().allMatch(Outcome.NOT_APPLICABLE::equals) ? NOT_APPLICABLE : UNDETERMINED;
        }
    }

    private static int count(Collection<Verdict> verdicts, Verdict verdict) {
        return (int) verdicts.stream().filter(verdict::equals).count();
    }
}
