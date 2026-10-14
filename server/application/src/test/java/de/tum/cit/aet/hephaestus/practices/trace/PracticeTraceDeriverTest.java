package de.tum.cit.aet.hephaestus.practices.trace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeTraceDeriverTest {
    @Test
    void aCaptureFailureRemainsAWithheldAssignment() {
        var assignment = ReviewPractice.builder()
                .workspaceId(1)
                .reviewId(UUID.randomUUID())
                .practiceId(1)
                .status(ReviewPracticeStatus.WITHHELD)
                .reason("SOURCE_UNAVAILABLE")
                .build();
        var trace = PracticeTraceDeriver.derive(new TraceInputs(
                "practice",
                DefinitionTestFixtures.definition().reviewOn(),
                DefinitionTestFixtures.definition().about(),
                true,
                FeedbackMode.AUTOMATIC,
                null,
                assignment,
                List.of()));
        assertThat(trace.results().couldNotDetermine()).isZero();
        assertThat(trace.withheldReasons())
                .singleElement()
                .satisfies(r -> assertThat(r.reason()).isEqualTo("SOURCE_UNAVAILABLE"));
    }

    @Test
    void feedbackModeOffDoesNotHideARecordedVerdict() {
        var result = Result.builder().outcome(ResultOutcome.MET).build();
        var trace = PracticeTraceDeriver.derive(new TraceInputs(
                "practice",
                DefinitionTestFixtures.definition().reviewOn(),
                DefinitionTestFixtures.definition().about(),
                true,
                FeedbackMode.OFF,
                null,
                null,
                List.of(result)));
        assertThat(trace.results().met()).isEqualTo(1);
        assertThat(trace.feedbackMode()).isEqualTo(FeedbackMode.OFF);
        assertThat(trace.withheldReasons()).isEmpty();
    }

    @Test
    void aPracticeWithNoReviewHasNoFabricatedOutcome() {
        var trace = PracticeTraceDeriver.derive(new TraceInputs(
                "practice",
                DefinitionTestFixtures.definition().reviewOn(),
                DefinitionTestFixtures.definition().about(),
                true,
                FeedbackMode.APPROVE_FIRST,
                null,
                null,
                List.of()));
        assertThat(trace.review()).isNull();
        assertThat(trace.results().met()
                        + trace.results().notMet()
                        + trace.results().doesNotApply()
                        + trace.results().couldNotDetermine())
                .isZero();
    }
}
