package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.ReviewRunRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * One page of review runs after the visibility gate. The runs a query returns are candidates: a run with no
 * result left for the reader is dropped, so candidates are read in slices of at least fifty until the
 * requested page and one run beyond it are filled or the candidates run out.
 *
 * @param content the runs on the requested page, each with the results the gate let through
 * @param hasNext whether a further visible run follows them
 */
public record VisibleRunPage(List<VisibleRun> content, boolean hasNext) {

    private static final int CANDIDATE_SLICE = 50;

    /**
     * @param reviewedAt when the run recorded its newest result the query counted
     * @param practiceResults what the gate let through, newest first
     */
    public record VisibleRun(UUID reviewId, Instant reviewedAt, List<PracticeResult> practiceResults) {}

    /**
     * @param candidates the runs the query returns for one slice, newest first
     * @param visibleResults the results of one slice's runs the gate lets through, by run; called
     *     once per slice, so it decides visibility and builds nothing the page does not show
     */
    public static <R extends ReviewRunRow> VisibleRunPage collect(
            Pageable pageable,
            Function<Pageable, Slice<R>> candidates,
            Function<List<R>, Map<UUID, List<PracticeResult>>> visibleResults) {
        int first = Math.multiplyExact(pageable.getPageNumber(), pageable.getPageSize());
        int required = Math.addExact(first, pageable.getPageSize() + 1);
        List<VisibleRun> visibleRuns = new ArrayList<>(required);
        int candidatePage = 0;
        boolean moreCandidates;
        do {
            Slice<R> slice = candidates.apply(
                    PageRequest.of(candidatePage++, Math.max(pageable.getPageSize(), CANDIDATE_SLICE)));
            if (slice.hasContent()) {
                Map<UUID, List<PracticeResult>> visible = visibleResults.apply(slice.getContent());
                for (R run : slice) {
                    List<PracticeResult> practiceResults = visible.getOrDefault(run.getJobId(), List.of());
                    if (!practiceResults.isEmpty()) {
                        visibleRuns.add(new VisibleRun(run.getJobId(), run.getReviewedAt(), practiceResults));
                    }
                }
            }
            moreCandidates = slice.hasNext();
        } while (visibleRuns.size() < required && moreCandidates);

        int end = Math.min(first + pageable.getPageSize(), visibleRuns.size());
        List<VisibleRun> content =
                first >= visibleRuns.size() ? List.of() : List.copyOf(visibleRuns.subList(first, end));
        return new VisibleRunPage(content, visibleRuns.size() > end);
    }
}
