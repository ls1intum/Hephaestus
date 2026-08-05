package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Final direction labels for normalized practice and area score deltas. */
@Tag("unit")
class AreaTrajectoryTest {

    @Test
    @DisplayName("classifies a positive standing delta as improving")
    void shouldClassifyPositiveDeltaAsImproving() {
        assertThat(AreaTrajectory.fromDelta(0.25)).isEqualTo(AreaTrajectory.IMPROVING);
    }

    @Test
    @DisplayName("classifies a negative standing delta as regressing")
    void shouldClassifyNegativeDeltaAsRegressing() {
        assertThat(AreaTrajectory.fromDelta(-0.25)).isEqualTo(AreaTrajectory.REGRESSING);
    }

    @Test
    @DisplayName("classifies an unchanged normalized standing as steady")
    void shouldClassifyZeroDeltaAsSteady() {
        assertThat(AreaTrajectory.fromDelta(0.0)).isEqualTo(AreaTrajectory.STEADY);
    }
}
