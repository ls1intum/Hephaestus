package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppFeedbackPreparer;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppFeedbackRouter;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bounds the composer is told, against the bounds that are actually enforced.
 *
 * <p>The staging side cannot import the lanes it stages for — composition is upstream of all three, and
 * a dependency back would make the packages mutually recursive — so each number it hands the model is a
 * restatement. A restatement is only safe if something fails when the two diverge, and that is this
 * file. A composer told it may write three cards for a surface that admits two spends a turn writing
 * text that is thrown away, and the developer never learns why the third thing was not said.
 */
class FeedbackCompositionCapsTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private JsonNode request;

    @BeforeEach
    void stageAnEventReview() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        FeedbackCompositionInputs.stage(files, ObservationOrigin.LIVE);
        request = objectMapper.readTree(
            new String(files.get(SandboxLayout.FEEDBACK_COMPOSITION_PATH), StandardCharsets.UTF_8)
        );
    }

    @Test
    void theInAppCapTheComposerIsToldIsTheCapThePreparerEnforces() {
        assertThat(request.get("channels").get(FeedbackChannel.IN_APP.name()).get("maxUnits").asInt()).isEqualTo(
            InAppFeedbackPreparer.TOP_N_PER_RECIPIENT
        );
    }

    @Test
    void thePatternBarTheComposerIsToldIsTheBarTheRouterEnforces() {
        assertThat(request.get("minDistinctArtifacts").asInt()).isEqualTo(InAppFeedbackRouter.CORROBORATION_ARTIFACTS);
    }
}
