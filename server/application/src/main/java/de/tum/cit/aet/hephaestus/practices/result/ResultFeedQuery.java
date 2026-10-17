package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultSort;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ResultFeedQuery(
        @Nullable String practiceSlug,
        @Nullable String groupSlug,
        @Nullable ResultOutcome outcome,
        @Nullable String itemKind,
        @Nullable Long itemId,
        @Nullable List<Severity> severities,
        boolean displayableOnly,
        ResultSort sort) {}
