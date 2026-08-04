package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AssessmentClaimCurrentnessTest {

    @Test
    void shouldDeriveCurrentnessFromDetectionSemantics() {
        assertThat(AssessmentClaimCurrentness.of("v2:one", "v2:one")).isEqualTo(AssessmentClaimCurrentness.CURRENT);
        assertThat(AssessmentClaimCurrentness.of("v1:one", "v2:one")).isEqualTo(AssessmentClaimCurrentness.STALE);
        assertThat(AssessmentClaimCurrentness.of(null, "v2:one")).isEqualTo(AssessmentClaimCurrentness.UNVERIFIABLE);
    }
}
