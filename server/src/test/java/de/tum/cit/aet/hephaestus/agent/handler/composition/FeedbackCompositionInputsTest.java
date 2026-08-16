package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The one file that decides whether a run composes at all, and which surfaces it may reach. */
class FeedbackCompositionInputsTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void anEventReviewOpensEveryLaneWithItsOwnCap() {
        JsonNode request = stage(ObservationOrigin.LIVE, FeedbackCompositionInputs.EVENT_REVIEW_CHANNELS);

        assertThat(request.get("enabled").asBoolean()).isTrue();
        for (FeedbackChannel channel : FeedbackChannel.values()) {
            JsonNode bounds = request.get("channels").get(channel.name());
            assertThat(bounds).as("bounds for %s", channel).isNotNull();
            assertThat(bounds.get("enabled").asBoolean()).isTrue();
            assertThat(bounds.get("maxUnits").asInt()).isPositive();
        }
    }

    /**
     * A sweep has no diff and no line to point at, so the public lane is closed — but it is closed by
     * being named and disabled, not by being left out. The per-surface rules are only legible in
     * contrast, and a lane that is simply missing reads as a lane the system does not have.
     */
    @Test
    void aLaneThisOccasionCannotReachIsNamedAndDisabledRatherThanOmitted() {
        JsonNode channels = stage(
            ObservationOrigin.LIVE,
            EnumSet.of(FeedbackChannel.IN_APP, FeedbackChannel.IN_CHAT)
        ).get("channels");

        assertThat(channels.get(FeedbackChannel.IN_CONTEXT.name())).isNotNull();
        assertThat(channels.get(FeedbackChannel.IN_CONTEXT.name()).get("enabled").asBoolean()).isFalse();
        assertThat(channels.get(FeedbackChannel.IN_APP.name()).get("enabled").asBoolean()).isTrue();
    }

    /**
     * Absence is the off switch, so a run that should not compose leaves no file at all rather than one
     * saying so — the runner never opens a second session, and the decision costs nothing.
     */
    @Test
    void aBackfillSweepStagesNothingAtAll() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        FeedbackCompositionInputs.stage(files, ObservationOrigin.BACKFILL);

        assertThat(files).doesNotContainKey(SandboxLayout.FEEDBACK_COMPOSITION_PATH);
    }

    @Test
    void anOccasionThatCanReachNoLaneStagesNothingAtAll() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        FeedbackCompositionInputs.stage(files, ObservationOrigin.LIVE, Set.of());

        assertThat(files).doesNotContainKey(SandboxLayout.FEEDBACK_COMPOSITION_PATH);
    }

    private JsonNode stage(ObservationOrigin origin, Set<FeedbackChannel> channels) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        FeedbackCompositionInputs.stage(files, origin, channels);
        byte[] staged = files.get(SandboxLayout.FEEDBACK_COMPOSITION_PATH);
        assertThat(staged).isNotNull();
        return objectMapper.readTree(new String(staged, StandardCharsets.UTF_8));
    }
}
