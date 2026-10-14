package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultSort;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;

public record ResultFeedFilterParams(
        @Nullable String practiceSlug,
        @Nullable String groupSlug,
        @Nullable ResultOutcome outcome,
        @Nullable String itemKind,
        @Positive @Nullable Long itemId,
        @Nullable List<Severity> severity,
        @Nullable Boolean displayableOnly,
        @Nullable ResultSort sort,
        @Nullable Integer page,
        @Nullable Integer size) {
    public Pageable pageable() {
        return QueryFilterSupport.pageable(page, size, 20);
    }

    public ResultFeedQuery toQuery() {
        if (itemKind != null) new ItemKind(itemKind);
        return new ResultFeedQuery(
                practiceSlug,
                groupSlug,
                outcome,
                itemKind,
                itemId,
                severity,
                Boolean.TRUE.equals(displayableOnly),
                sort == null ? ResultSort.NEWEST : sort);
    }
}
