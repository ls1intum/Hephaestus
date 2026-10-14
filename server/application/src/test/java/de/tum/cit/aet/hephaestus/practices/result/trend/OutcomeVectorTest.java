package de.tum.cit.aet.hephaestus.practices.result.trend;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class OutcomeVectorTest {

    @Test
    void shouldMapEveryOutcomeToTheSharedUiContract() {
        assertThat(OutcomeVector.of(ResultOutcome.MET)).isEqualTo(new OutcomeVector(1, 0, 0, 0));
        assertThat(OutcomeVector.of(ResultOutcome.NOT_MET)).isEqualTo(new OutcomeVector(0, 1, 0, 0));
        assertThat(OutcomeVector.of(ResultOutcome.DOES_NOT_APPLY)).isEqualTo(new OutcomeVector(0, 0, 1, 0));
        assertThat(OutcomeVector.of(ResultOutcome.COULD_NOT_DETERMINE)).isEqualTo(new OutcomeVector(0, 0, 0, 1));
    }
}
