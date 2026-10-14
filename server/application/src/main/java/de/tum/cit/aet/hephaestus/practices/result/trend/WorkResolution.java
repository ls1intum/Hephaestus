package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * How a developer's own work resolves a piece of practice feedback: after the feedback was prepared,
 * {@link #CLEAN_NEEDED} pieces of reviewed work in a row come back clean on the practice.
 *
 * <p>Read at request time off the results and never stored, so a re-review that changes what a piece
 * of work's latest run said changes the answer with it. The unit is a piece of reviewed work, bundled as the
 * trend bundles it: one opportunity per piece of work, read off its newest live run
 * with endedAt absent, so a pull request reviewed three times is one piece of work, not three. An
 * opportunity that produced no verdict is skipped rather than counted as either side, exactly as
 * {@link PracticeTrend#cleanWork()} skips it; one that raised a problem starts the count over. The first run of
 * {@link #CLEAN_NEEDED} clean opportunities resolves the feedback, and what came after it does not un-resolve it:
 * a later slip is new feedback's business.
 *
 * @param cleanWork the pieces of work in the current clean run, oldest first, at most {@link #CLEAN_NEEDED};
 *     empty again after a problem, and exactly the resolving pieces once the feedback is resolved
 * @param resolvedAt when the piece of work that completed the run was reviewed, or null while the work has
 *     not resolved the feedback
 * @param problemWork the pieces of work that raised a problem since the last clean one, newest first: what
 *     the clean run stands at zero because of. Empty whenever {@code cleanWork} is not, so the two together
 *     say both how far the run got and what put it back.
 */
public record WorkResolution(List<Work> cleanWork, @Nullable Instant resolvedAt, List<Work> problemWork) {

    /** Clean pieces of work in a row that resolve a piece of feedback. */
    public static final int CLEAN_NEEDED = 3;

    /** No clean work yet. */
    public static final WorkResolution NONE = new WorkResolution(List.of(), null, List.of());

    /**
     * One practice's opportunities since a horizon, bundled once and read by every piece of feedback about
     * the practice, so a page with several cards on one practice bundles its work once rather than per card.
     */
    public static final class Opportunities {

        private final List<EvidenceOpportunity> oldestFirst;

        private Opportunities(List<EvidenceOpportunity> oldestFirst) {
            this.oldestFirst = oldestFirst;
        }

        /** @param horizon the moment before which a result is not an opportunity at all */
        public static Opportunities of(List<ResultView> practiceResults, Instant horizon) {
            return new Opportunities(
                    OpportunityBundler.opportunities(practiceResults, horizon).reversed());
        }

        /** How the work has answered feedback prepared at {@code preparedAt}. */
        public WorkResolution resolve(Instant preparedAt) {
            List<Work> clean = new ArrayList<>();
            List<Work> problems = new ArrayList<>();
            for (EvidenceOpportunity opportunity : oldestFirst) {
                if (!opportunity.occurredAt().isAfter(preparedAt) || !opportunity.decided()) {
                    continue;
                }
                Work work = new Work(
                        opportunity.itemKind(), opportunity.itemId(), opportunity.reviewId(), opportunity.occurredAt());
                if (!opportunity.clean()) {
                    clean.clear();
                    problems.add(work);
                    continue;
                }
                // problemWork is what the count stands at zero because of, and a clean piece moves it off zero.
                problems.clear();
                clean.add(work);
                if (clean.size() == CLEAN_NEEDED) {
                    return new WorkResolution(List.copyOf(clean), opportunity.occurredAt(), List.of());
                }
            }
            return clean.isEmpty() && problems.isEmpty()
                    ? NONE
                    : new WorkResolution(List.copyOf(clean), null, Work.newestFirst(problems.stream()));
        }
    }

    /**
     * One piece of reviewed work as the resolution and profile rules see it: its identity, the run that
     * reviewed it and when. The job id is what later resolves the label and the link; the rules themselves
     * never need them.
     */
    public record Work(ItemKind kind, long itemId, UUID reviewId, Instant at) {
        public static Work of(ResultView view) {
            return new Work(
                    new ItemKind(view.item().kind()),
                    view.result().getItemId(),
                    view.result().getReviewId(),
                    view.result().getRecordedAt());
        }

        public long key() {
            return itemId;
        }

        /** One entry per piece of work, at its newest review, newest first. */
        public static List<Work> newestFirst(Stream<Work> work) {
            Map<Long, Work> byWork = new LinkedHashMap<>();
            work.forEach(ref ->
                    byWork.merge(ref.key(), ref, (left, right) -> left.at().isAfter(right.at()) ? left : right));
            return byWork.values().stream()
                    .sorted(Comparator.comparing(Work::at).reversed())
                    .toList();
        }
    }
}
