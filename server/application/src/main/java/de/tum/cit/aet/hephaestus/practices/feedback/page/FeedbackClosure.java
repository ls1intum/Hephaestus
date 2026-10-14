package de.tum.cit.aet.hephaestus.practices.feedback.page;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackClosedBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * How a piece of practice-page feedback stopped being open: resolved by the work or by the developer, or closed
 * because its practice changed its review criteria, whichever came first
 * ({@code docs/contributor/practice-review-glossary.mdx} § How feedback resolves).
 */
public record FeedbackClosure(Instant at, FeedbackClosedBy by) {

    /** The first of the three that happened, or {@code null} while none has. */
    public static @Nullable FeedbackClosure of(
            @Nullable Instant resolvedByWorkAt,
            @Nullable Instant resolvedByDeveloperAt,
            @Nullable Instant practiceChangedAt) {
        List<FeedbackClosure> happened = new ArrayList<>(3);
        if (resolvedByWorkAt != null) {
            happened.add(new FeedbackClosure(resolvedByWorkAt, FeedbackClosedBy.WORK));
        }
        if (resolvedByDeveloperAt != null) {
            happened.add(new FeedbackClosure(resolvedByDeveloperAt, FeedbackClosedBy.DEVELOPER));
        }
        if (practiceChangedAt != null) {
            happened.add(new FeedbackClosure(practiceChangedAt, FeedbackClosedBy.PRACTICE_CHANGED));
        }
        return happened.stream()
                .min(Comparator.comparing(FeedbackClosure::at).thenComparing(FeedbackClosure::by))
                .orElse(null);
    }
}
