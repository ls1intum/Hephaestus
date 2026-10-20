package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One bounded read of the private feedback one developer received or has queued, for private composition. It is not
 * the developer's whole history: {@code hasMore} and {@code withoutCopyAuthority} say what it leaves out.
 *
 * @param withoutCopyAuthority selected rows left out whole because their support may not be copied for this purpose
 */
public record PrivateFeedbackHistory(
        Instant readAt, int maxEntries, boolean hasMore, int withoutCopyAuthority, List<Entry> entries) {
    public PrivateFeedbackHistory {
        entries = List.copyOf(entries);
    }

    /**
     * The feedback's body is what was said, where its words may be copied. {@code currentness} is whether the claim it
     * rests on still holds: a stale claim's words are still a record of what was said.
     *
     * @param replaceablePracticeSlug for conversation feedback still queued, the practice a new piece may replace it
     *     for; null when it cannot be replaced
     */
    public record Entry(
            FeedbackHistoryEntry feedback,
            List<Basis> basedOn,
            Currentness currentness,
            boolean withdrawn,
            @Nullable String replaceablePracticeSlug) {
        public Entry {
            basedOn = List.copyOf(basedOn);
        }
    }

    /** One private destination that the same composition read opened for one practice. */
    public record Route(long practiceId, FeedbackDestination destination) {
        public Route {
            if (destination == FeedbackDestination.ON_WORK)
                throw new IllegalArgumentException("Private history has no route on the work.");
        }
    }

    public record Basis(
            UUID resultId, String practiceSlug, @Nullable Long practiceVersionId, ResultOutcome outcome) {}
}
