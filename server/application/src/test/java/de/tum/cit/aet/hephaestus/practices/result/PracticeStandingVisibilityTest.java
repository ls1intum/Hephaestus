package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeStandingVisibilityTest {
    @Test
    void aSilenceIsNotRenderedAsAVerdict() {
        assertThat(StandingScale.isVerdict(PracticeStanding.NOT_OBSERVED)).isFalse();
        assertThat(StandingScale.isVerdict(PracticeStanding.NO_OPPORTUNITY)).isFalse();
    }

    @Test
    void aVerdictCanBeMixedWithoutInventingAStrength() {
        assertThat(StandingScale.classify(0.4)).isEqualTo(PracticeStanding.MIXED);
        assertThat(StandingScale.classify(0.81)).isEqualTo(PracticeStanding.STRENGTH);
        assertThat(StandingScale.classify(0.2)).isEqualTo(PracticeStanding.DEVELOPING);
    }
}
