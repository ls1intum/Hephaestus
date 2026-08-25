package de.tum.cit.aet.hephaestus.integration.core.framework;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every {@link FeedbackLane} must carry an enforcement rule.
 *
 * <p>A lane added to the enum and to neither of {@link ReviewContractValidator}'s two collections is not
 * rejected — it is simply never checked, so an integration declaring it would pass validation and then
 * deliver nowhere. This is a test rather than a fail-fast in a static initializer because that would only
 * move the discovery to runtime, including the webhook pod, whose missed push events cannot be
 * redelivered.
 */
class ReviewContractLaneRulesTest extends BaseUnitTest {

    @Test
    @DisplayName("every feedback lane is either deliverable with a capability or reserved to Hephaestus")
    void everyFeedbackLaneIsClassified() {
        Set<FeedbackLane> unclassified = EnumSet.allOf(FeedbackLane.class);
        unclassified.removeAll(ReviewContractValidator.LANE_CAPABILITIES.keySet());
        unclassified.removeAll(ReviewContractValidator.HEPHAESTUS_OWNED_LANES);

        assertThat(unclassified)
            .as("add each lane to LANE_CAPABILITIES or HEPHAESTUS_OWNED_LANES in ReviewContractValidator")
            .isEmpty();
    }

    @Test
    @DisplayName("no lane is both vendor-deliverable and reserved to Hephaestus")
    void noLaneIsClassifiedTwice() {
        assertThat(ReviewContractValidator.LANE_CAPABILITIES.keySet())
            .as("a lane a vendor can be required to hold a capability for cannot also be unreachable to vendors")
            .doesNotContainAnyElementsOf(ReviewContractValidator.HEPHAESTUS_OWNED_LANES);
    }
}
