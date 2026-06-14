package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sign-neutral verdict only becomes "problem" or "strength" once read through a practice's
 * {@link Polarity} (ADR 0021, F-6). These cases lock that direction for every (polarity, verdict)
 * combination so a future anti-pattern practice cannot silently invert.
 */
class PolarityTest extends BaseUnitTest {

    @Test
    @DisplayName("DESIRABLE: NOT_OBSERVED is the problem, OBSERVED is the strength")
    void desirable() {
        assertThat(Polarity.DESIRABLE.isProblem(Verdict.NOT_OBSERVED)).isTrue();
        assertThat(Polarity.DESIRABLE.isProblem(Verdict.OBSERVED)).isFalse();
        assertThat(Polarity.DESIRABLE.isStrength(Verdict.OBSERVED)).isTrue();
        assertThat(Polarity.DESIRABLE.isStrength(Verdict.NOT_OBSERVED)).isFalse();
    }

    @Test
    @DisplayName("UNDESIRABLE: OBSERVED (did the bad thing) is the problem, NOT_OBSERVED is the strength")
    void undesirable() {
        assertThat(Polarity.UNDESIRABLE.isProblem(Verdict.OBSERVED)).isTrue();
        assertThat(Polarity.UNDESIRABLE.isProblem(Verdict.NOT_OBSERVED)).isFalse();
        assertThat(Polarity.UNDESIRABLE.isStrength(Verdict.NOT_OBSERVED)).isTrue();
        assertThat(Polarity.UNDESIRABLE.isStrength(Verdict.OBSERVED)).isFalse();
    }

    @Test
    @DisplayName("MIXED follows the desirable direction (NOT_OBSERVED is the problem)")
    void mixed() {
        assertThat(Polarity.MIXED.isProblem(Verdict.NOT_OBSERVED)).isTrue();
        assertThat(Polarity.MIXED.isStrength(Verdict.OBSERVED)).isTrue();
    }

    @Test
    @DisplayName("NOT_APPLICABLE is never a problem nor a strength, regardless of polarity")
    void notApplicableIsNeither() {
        for (Polarity p : Polarity.values()) {
            assertThat(p.isProblem(Verdict.NOT_APPLICABLE)).as("%s problem", p).isFalse();
            assertThat(p.isStrength(Verdict.NOT_APPLICABLE)).as("%s strength", p).isFalse();
        }
    }
}
