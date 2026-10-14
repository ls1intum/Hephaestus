package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoverageOmission(
        String what,
        int count,
        OmissionReason reason,
        @Nullable List<String> items) {
    public CoverageOmission {
        items = items == null ? null : List.copyOf(items);
    }
}
