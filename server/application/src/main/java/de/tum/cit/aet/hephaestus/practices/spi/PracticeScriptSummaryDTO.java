package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeScriptSummaryDTO(
        String practiceSlug,
        String practiceName,
        @Nullable PracticeScriptAsOfDTO asOf,
        boolean scriptChanged,
        List<PracticeScriptNeedDTO> needs) {
    public PracticeScriptSummaryDTO {
        needs = List.copyOf(needs);
    }
}
