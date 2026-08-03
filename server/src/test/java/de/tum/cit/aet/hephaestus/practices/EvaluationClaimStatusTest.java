package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class EvaluationClaimStatusTest {

    @Test
    void shouldDeriveCurrentnessFromPinnedAndCurrentRevision() {
        assertThat(EvaluationClaimStatus.of(7L, 7L)).isEqualTo(EvaluationClaimStatus.CURRENT);
        assertThat(EvaluationClaimStatus.of(7L, 8L)).isEqualTo(EvaluationClaimStatus.STALE);
        assertThat(EvaluationClaimStatus.of(null, 8L)).isEqualTo(EvaluationClaimStatus.UNVERIFIABLE);
    }
}
