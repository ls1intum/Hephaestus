package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chooses the valid review that speaks for work or an exact claim. Earlier results remain immutable history.
 * The pipeline documentation owns the retained occasion proof and completion fallback.
 */
public final class LatestRun {

    private LatestRun() {}

    /**
     * The run that reviewed these rows' work last: the latest occasion, where the run proved which one it read
     * ({@link PracticeResult#getOccasionAt()}), else the latest completion. A run of an older occasion that completes
     * later does not speak over a newer one. The caller passes at least one row.
     *
     * <p>The tie-break compares the job id as its canonical string because the same rule is also written in SQL
     * ({@code PracticeResultRepository#LATEST_RUN_ORDER}), and PostgreSQL orders {@code uuid} byte-wise while
     * {@link UUID#compareTo} orders its two halves as signed longs — the two would disagree on exactly the tie
     * this break exists for.
     */
    public static UUID of(Collection<PracticeResult> practiceResults) {
        return practiceResults.stream()
                .max(Comparator.comparing(LatestRun::occasion)
                        .thenComparing(PracticeResult::getObservedAt)
                        .thenComparing(
                                practiceResult -> practiceResult.getAgentJobId().toString()))
                .map(PracticeResult::getAgentJobId)
                .orElseThrow();
    }

    private static Instant occasion(PracticeResult practiceResult) {
        Instant occasion = practiceResult.getOccasionAt();
        return occasion != null ? occasion : practiceResult.getObservedAt();
    }

    /**
     * Narrows one practice's window to each piece of work's newest run, in the window's order: a row from an
     * earlier run of the same work is dropped, so a problem a later review no longer found is not in the answer.
     */
    public static List<PracticeResult> perWork(Collection<PracticeResult> practiceResults) {
        return latest(practiceResults, ReviewedWorkKey::of);
    }

    /**
     * Narrows a developer's whole window to the newest run of each claim: one practice on one piece of work about one
     * person in one workspace, read within one origin class. A run does not necessarily evaluate every practice, so
     * correlating on the work alone would let a later run supersede a verdict it never re-examined, and a partial
     * capture or a timeout would read like a fixed lapse. A campaign's reading and a live reading of the same work are
     * two claims: origin-blind, a later campaign would erase already-delivered live feedback from the answer. A
     * review of one person does not speak for another reviewed on the same work, so a window holding several people
     * keeps each one's newest run.
     */
    public static List<PracticeResult> perClaim(Collection<PracticeResult> practiceResults) {
        return latest(practiceResults, Claim::of);
    }

    /**
     * Narrows a developer's window to each claim's newest LIVE run: what the trend, the standing and the work
     * resolution count. A requested or backfilled run is a self-selected sample ({@link ResultOrigin}), so it
     * neither counts nor hides the live run before it. {@link #perClaim} still decides what is quoted, so a
     * requested re-review that came back clean stops a live problem from being cited.
     */
    public static List<PracticeResult> perLiveClaim(Collection<PracticeResult> practiceResults) {
        return perClaim(practiceResults.stream()
                .filter(practiceResult -> practiceResult.getOrigin() == ResultOrigin.LIVE)
                .toList());
    }

    private static <K> List<PracticeResult> latest(
            Collection<PracticeResult> practiceResults, Function<PracticeResult, K> key) {
        Map<K, UUID> latestByKey = practiceResults.stream()
                .collect(Collectors.groupingBy(key, Collectors.collectingAndThen(Collectors.toList(), LatestRun::of)));
        return practiceResults.stream()
                .filter(practiceResult ->
                        practiceResult.getAgentJobId().equals(latestByKey.get(key.apply(practiceResult))))
                .toList();
    }

    private record Claim(
            Long workspaceId, Long aboutUserId, String practiceSlug, ReviewedWorkKey work, boolean backfilled) {
        static Claim of(PracticeResult practiceResult) {
            return new Claim(
                    practiceResult.getWorkspaceId(),
                    practiceResult.getAboutUserId(),
                    practiceResult.getPractice().getSlug(),
                    ReviewedWorkKey.of(practiceResult),
                    practiceResult.getOrigin() == ResultOrigin.BACKFILL);
        }
    }
}
