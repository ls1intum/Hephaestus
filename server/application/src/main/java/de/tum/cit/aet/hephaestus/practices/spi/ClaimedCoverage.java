package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaimedCoverage(
        boolean complete,
        @Nullable List<CoverageOmission> omitted,
        int selected,
        int checked,
        @Nullable Integer total) implements ResultCoverage {
    public ClaimedCoverage {
        omitted = omitted == null ? null : List.copyOf(omitted);
    }
}
