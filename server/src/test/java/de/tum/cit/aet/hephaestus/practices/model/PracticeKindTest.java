package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sign-neutral observation only becomes "problem" or "strength" once read through a practice's
 * {@link PracticeKind} (ADR 0021, F-6). These cases lock that direction for every (kind, observation)
 * combination so a future anti-pattern practice cannot silently invert.
 */
class PracticeKindTest extends BaseUnitTest {

    @Test
    @DisplayName("GOOD_PRACTICE: NOT_OBSERVED is the problem, OBSERVED is the strength")
    void goodPractice() {
        assertThat(PracticeKind.GOOD_PRACTICE.isProblem(Presence.NOT_OBSERVED)).isTrue();
        assertThat(PracticeKind.GOOD_PRACTICE.isProblem(Presence.OBSERVED)).isFalse();
        assertThat(PracticeKind.GOOD_PRACTICE.isStrength(Presence.OBSERVED)).isTrue();
        assertThat(PracticeKind.GOOD_PRACTICE.isStrength(Presence.NOT_OBSERVED)).isFalse();
    }

    @Test
    @DisplayName("BAD_PRACTICE: OBSERVED (did the bad thing) is the problem, NOT_OBSERVED is the strength")
    void badPractice() {
        assertThat(PracticeKind.BAD_PRACTICE.isProblem(Presence.OBSERVED)).isTrue();
        assertThat(PracticeKind.BAD_PRACTICE.isProblem(Presence.NOT_OBSERVED)).isFalse();
        assertThat(PracticeKind.BAD_PRACTICE.isStrength(Presence.NOT_OBSERVED)).isTrue();
        assertThat(PracticeKind.BAD_PRACTICE.isStrength(Presence.OBSERVED)).isFalse();
    }

    @Test
    @DisplayName("CONTEXTUAL follows the desirable direction (NOT_OBSERVED is the problem)")
    void mixed() {
        assertThat(PracticeKind.CONTEXTUAL.isProblem(Presence.NOT_OBSERVED)).isTrue();
        assertThat(PracticeKind.CONTEXTUAL.isStrength(Presence.OBSERVED)).isTrue();
    }

    @Test
    @DisplayName("NOT_APPLICABLE is never a problem nor a strength, regardless of kind")
    void notApplicableIsNeither() {
        for (PracticeKind p : PracticeKind.values()) {
            assertThat(p.isProblem(Presence.NOT_APPLICABLE)).as("%s problem", p).isFalse();
            assertThat(p.isStrength(Presence.NOT_APPLICABLE)).as("%s strength", p).isFalse();
        }
    }
}
