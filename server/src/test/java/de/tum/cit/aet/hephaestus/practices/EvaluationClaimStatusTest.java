package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class EvaluationClaimStatusTest {

    @Test
    void shouldDeriveCurrentnessFromDetectionSemantics() {
        assertThat(EvaluationClaimStatus.of("v2:one", "v2:one")).isEqualTo(EvaluationClaimStatus.CURRENT);
        assertThat(EvaluationClaimStatus.of("v1:one", "v2:one")).isEqualTo(EvaluationClaimStatus.STALE);
        assertThat(EvaluationClaimStatus.of(null, "v2:one")).isEqualTo(EvaluationClaimStatus.UNVERIFIABLE);
    }
}
