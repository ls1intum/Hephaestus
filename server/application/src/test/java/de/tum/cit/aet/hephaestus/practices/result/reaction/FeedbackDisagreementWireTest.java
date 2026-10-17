package de.tum.cit.aet.hephaestus.practices.result.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackDisagreementDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FeedbackDisagreementWireTest extends BaseUnitTest {
    @Test
    void disagreementCarriesItsActualDestinationAndNoResultMutation() {
        var dto = new FeedbackDisagreementDTO(
                UUID.randomUUID(), FeedbackDestination.PRACTICE_PAGE, "This context is missing.", Instant.EPOCH);
        var tree = new JsonMapper().valueToTree(dto);
        assertThat(tree.path("destination").asString()).isEqualTo("PRACTICE_PAGE");
        assertThat(tree.has("comment")).isTrue();
        assertThat(tree.has("reason")).isFalse();
        assertThat(tree.has("resultId")).isFalse();
    }
}
