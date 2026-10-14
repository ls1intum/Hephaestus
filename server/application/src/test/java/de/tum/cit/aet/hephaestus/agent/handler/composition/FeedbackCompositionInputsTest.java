package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
class FeedbackCompositionInputsTest {

    @Test
    void shouldStageOnlyTheFinalDestinationContract() {
        Map<String, byte[]> files = new HashMap<>();
        var mapper = new ObjectMapper();
        FeedbackCompositionInputs.stage(
                files,
                ReviewKind.ITEM,
                ReviewOrigin.EVENT,
                Set.of(FeedbackDestination.ON_WORK, FeedbackDestination.PRACTICE_PAGE),
                mapper);
        var input = mapper.readTree(files.get(SandboxLayout.FEEDBACK_COMPOSITION_PATH));
        assertThat(input.path("contractVersion").asInt()).isEqualTo(3);
        assertThat(input.path("destinations").toString()).isEqualTo("[\"ON_WORK\",\"PRACTICE_PAGE\"]");
        assertThat(input.path("minDistinctItems").asInt()).isEqualTo(2);
    }

    @Test
    void shouldKeepScanAndBackfillQuiet() {
        Map<String, byte[]> files = new HashMap<>();
        var mapper = new ObjectMapper();
        FeedbackCompositionInputs.stage(
                files, ReviewKind.SCAN, ReviewOrigin.REQUEST, Set.of(FeedbackDestination.ON_WORK), mapper);
        FeedbackCompositionInputs.stage(
                files, ReviewKind.ITEM, ReviewOrigin.BACKFILL, Set.of(FeedbackDestination.CONVERSATION), mapper);
        assertThat(files).isEmpty();
    }
}
