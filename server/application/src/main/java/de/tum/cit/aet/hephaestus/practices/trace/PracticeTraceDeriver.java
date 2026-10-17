package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.practices.review.ReviewSummaryQueryService;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.PracticeTraceEntryDTO;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class PracticeTraceDeriver {
    private PracticeTraceDeriver() {}

    static PracticeTraceEntryDTO derive(TraceInputs input) {
        var assignment = input.assignment();
        var reason = assignment == null ? null : assignment.getReason();
        return new PracticeTraceEntryDTO(
                input.practiceSlug(),
                input.feedbackMode(),
                input.reviewOn(),
                input.about(),
                input.review(),
                ReviewSummaryQueryService.counts(input.results()),
                reasons(input, reason));
    }

    private static List<ReasonCountDTO> reasons(TraceInputs input, @Nullable String reason) {
        var reasons = new ArrayList<ReasonCountDTO>();
        if (reason != null) reasons.add(new ReasonCountDTO(reason, reason, 1));
        if (!input.definitionKnown())
            reasons.add(new ReasonCountDTO(
                    "HISTORICAL_DEFINITION_UNKNOWN", "The historical practice definition is incomplete.", 1));
        return List.copyOf(reasons);
    }
}
