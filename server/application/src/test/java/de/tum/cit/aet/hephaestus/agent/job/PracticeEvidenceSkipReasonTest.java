package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeEvidenceSkipReasonTest {

    @Test
    @DisplayName("publishes exactly the reasons a readiness report can record, at either level")
    void coversBothReadinessReasonEnums() {
        assertThat(Arrays.stream(PracticeEvidenceSkipReason.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(Stream.concat(
                                Arrays.stream(SourceReadinessReason.values()).map(Enum::name),
                                Arrays.stream(AutomatedReviewReadinessReason.values())
                                        .map(Enum::name))
                        .toList());
    }
}
