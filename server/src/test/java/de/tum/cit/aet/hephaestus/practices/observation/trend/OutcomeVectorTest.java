package de.tum.cit.aet.hephaestus.practices.observation.trend;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class OutcomeVectorTest {

    @Test
    void shouldMapAllPresenceAssessmentCellsToTheSharedUiContract() {
        assertThat(OutcomeVector.of(Presence.PRESENT, Assessment.GOOD)).isEqualTo(new OutcomeVector(1, 0, 0, 0, 0));
        assertThat(OutcomeVector.of(Presence.ABSENT, Assessment.GOOD)).isEqualTo(new OutcomeVector(0, 1, 0, 0, 0));
        assertThat(OutcomeVector.of(Presence.PRESENT, Assessment.BAD)).isEqualTo(new OutcomeVector(0, 0, 1, 0, 0));
        assertThat(OutcomeVector.of(Presence.ABSENT, Assessment.BAD)).isEqualTo(new OutcomeVector(0, 0, 0, 1, 0));
        assertThat(OutcomeVector.of(Presence.NOT_APPLICABLE, null)).isEqualTo(new OutcomeVector(0, 0, 0, 0, 1));
    }
}
