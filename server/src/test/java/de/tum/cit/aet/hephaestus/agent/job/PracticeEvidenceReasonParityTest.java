package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.job.PracticeEvidenceOutcomeDTO.PracticeEvidenceBlockDTO;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A readiness reason can come from either enum, so the DTO pins the union by hand and the generated
 * client turns it into a closed type. Adding a constant without extending that list would ship the raw
 * name to a workspace admin with nothing failing.
 */
@Tag("unit")
class PracticeEvidenceReasonParityTest {

    @Test
    @DisplayName("the reason schema admits exactly the constants the runtime can record")
    void schemaMatchesBothReasonEnums() throws NoSuchMethodException {
        Schema schema = PracticeEvidenceBlockDTO.class.getMethod("reasonCode").getAnnotation(Schema.class);

        assertThat(Set.of(schema.allowableValues())).isEqualTo(
            Stream.concat(
                Arrays.stream(SourceReadinessReason.values()).map(Enum::name),
                Arrays.stream(AutomatedReviewReadinessReason.values()).map(Enum::name)
            ).collect(Collectors.toSet())
        );
    }
}
