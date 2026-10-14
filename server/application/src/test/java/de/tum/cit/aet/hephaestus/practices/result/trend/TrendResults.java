package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.review.ReviewTestData;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Instant;
import java.util.UUID;

public final class TrendResults {
    private TrendResults() {}

    public static ResultView clean(long itemId, String at) {
        return judged(itemId, at, ResultOutcome.MET);
    }

    public static ResultView clean(long itemId, String at, ItemKind kind) {
        return result(itemId, UUID.randomUUID(), at, kind, ResultOutcome.MET);
    }

    public static ResultView clean(long itemId, UUID reviewId, String at) {
        return judged(itemId, reviewId, at, ResultOutcome.MET);
    }

    public static ResultView judged(long itemId, String at, ResultOutcome outcome) {
        return judged(itemId, UUID.randomUUID(), at, outcome);
    }

    public static ResultView judged(long itemId, UUID reviewId, String at, ResultOutcome outcome) {
        return result(itemId, reviewId, at, new ItemKind("scm.pull_request"), outcome);
    }

    public static ResultView problem(long itemId, String at) {
        return judged(itemId, at, ResultOutcome.NOT_MET);
    }

    public static ResultView problem(long itemId, UUID reviewId, String at) {
        return judged(itemId, reviewId, at, ResultOutcome.NOT_MET);
    }

    public static ResultView noVerdict(long itemId, String at) {
        return judged(itemId, at, ResultOutcome.DOES_NOT_APPLY);
    }

    public static ResultView noVerdict(long itemId, UUID reviewId, String at) {
        return judged(itemId, reviewId, at, ResultOutcome.DOES_NOT_APPLY);
    }

    private static ResultView result(long itemId, UUID reviewId, String at, ItemKind kind, ResultOutcome outcome) {
        return ReviewTestData.view(itemId, reviewId, Instant.parse(at), kind, outcome);
    }
}
