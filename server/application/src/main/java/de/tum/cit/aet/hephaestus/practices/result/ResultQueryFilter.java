package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ResultQueryFilter(
        @Nullable UUID reviewId,
        @Nullable Long itemId,
        @Nullable String itemKind,
        @Nullable String practiceSlug,
        @Nullable String groupSlug,
        @Nullable Long developerId,
        @Nullable List<ResultOutcome> outcomes,
        @Nullable List<Severity> severities,
        @Nullable Currentness currentness,
        @Nullable Boolean markedIncorrect,
        @Nullable Boolean accepted,
        @Nullable Boolean disagreed,
        @Nullable Instant from,
        @Nullable Instant to) {
    public static final ResultQueryFilter ALL =
            new ResultQueryFilter(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
}
