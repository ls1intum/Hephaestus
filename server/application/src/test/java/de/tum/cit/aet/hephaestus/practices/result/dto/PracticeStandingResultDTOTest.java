package de.tum.cit.aet.hephaestus.practices.result.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeStandingResultDTOTest extends BaseUnitTest {
    @Test
    void wireUsesCanonicalItemIdentityAndOrigin() {
        var item = new ItemRefDTO(
                "9223372036854775807", "scm.file", "1", null, "src/Main.java", null, null, null, "src/Main.java", null);
        var dto = new PracticeStandingResultDTO(
                UUID.randomUUID(),
                "Recorded result",
                null,
                Severity.MINOR,
                ResultOutcome.NOT_MET,
                item,
                ReviewOrigin.SCHEDULE);
        var tree = new JsonMapper().valueToTree(dto);
        assertThat(tree.path("item").path("id").asString()).isEqualTo("9223372036854775807");
        assertThat(tree.path("origin").asString()).isEqualTo("SCHEDULE");
        assertThat(tree.has("artifactId")).isFalse();
        assertThat(tree.has("agentJobId")).isFalse();
        assertThat(tree.has("deliveredFeedback")).isTrue();
        assertThat(tree.path("deliveredFeedback").isNull()).isTrue();
    }
}
