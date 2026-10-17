package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

record TraceInputs(
        String practiceSlug,
        ReviewOn reviewOn,
        @Nullable AboutRole about,
        boolean definitionKnown,
        FeedbackMode feedbackMode,
        @Nullable ReviewRefDTO review,
        @Nullable ReviewPractice assignment,
        List<Result> results) {
    public TraceInputs {
        results = List.copyOf(results);
    }
}
