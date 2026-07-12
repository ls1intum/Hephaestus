package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single shared standing derivation used by both the reflection cards and the mentor overview. These
 * assertions pin the criterion-referenced 2×2 (problems × strengths) plus the NO_ACTIVITY extension and the
 * needs-attention triage predicate.
 */
class PracticeStatusDeriverTest extends BaseUnitTest {

    @Test
    @DisplayName("problems and strengths -> MIXED")
    void mixed() {
        assertThat(PracticeStatusDeriver.derive(true, true)).isEqualTo(PracticeStatus.MIXED);
    }

    @Test
    @DisplayName("problems only -> DEVELOPING")
    void developing() {
        assertThat(PracticeStatusDeriver.derive(true, false)).isEqualTo(PracticeStatus.DEVELOPING);
    }

    @Test
    @DisplayName("strengths only -> STRENGTH")
    void strength() {
        assertThat(PracticeStatusDeriver.derive(false, true)).isEqualTo(PracticeStatus.STRENGTH);
    }

    @Test
    @DisplayName("neither problems nor strengths -> NO_ACTIVITY")
    void noActivity() {
        assertThat(PracticeStatusDeriver.derive(false, false)).isEqualTo(PracticeStatus.NO_ACTIVITY);
    }

    @Test
    @DisplayName("needs attention only for DEVELOPING and MIXED")
    void needsAttention() {
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.DEVELOPING)).isTrue();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.MIXED)).isTrue();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.STRENGTH)).isFalse();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.NO_ACTIVITY)).isFalse();
    }
}
