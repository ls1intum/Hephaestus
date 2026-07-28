package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The single decision every practice surface routes through. Worth exhaustive coverage because it is
 * four-by-four small and because a card, a roster cell and a health bucket disagreeing about the same
 * developer is the kind of bug nobody reports and everybody notices.
 */
class PracticeStatusDeriverTest extends BaseUnitTest {

    @ParameterizedTest(name = "problems={0} strengths={1} -> {2}")
    @CsvSource({ "true,true,MIXED", "true,false,DEVELOPING", "false,true,STRENGTH", "false,false,NO_ACTIVITY" })
    void deriveCoversEveryCombination(boolean hasProblems, boolean hasStrengths, PracticeStatus expected) {
        assertThat(PracticeStatusDeriver.derive(hasProblems, hasStrengths)).isEqualTo(expected);
    }

    @Test
    @DisplayName("needsAttention flags unresolved gaps only — a strength or a quiet window is not a demerit")
    void needsAttentionFlagsGapsOnly() {
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.DEVELOPING)).isTrue();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.MIXED)).isTrue();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.STRENGTH)).isFalse();
        assertThat(PracticeStatusDeriver.needsAttention(PracticeStatus.NO_ACTIVITY)).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} yields {2}")
    @CsvSource(
        {
            // Fewer unresolved problems than before.
            "DEVELOPING,STRENGTH,IMPROVING",
            "DEVELOPING,MIXED,IMPROVING",
            "MIXED,STRENGTH,IMPROVING",
            // More.
            "STRENGTH,DEVELOPING,WORSENING",
            "STRENGTH,MIXED,WORSENING",
            "MIXED,DEVELOPING,WORSENING",
            // Unchanged.
            "STRENGTH,STRENGTH,STEADY",
            "MIXED,MIXED,STEADY",
            "DEVELOPING,DEVELOPING,STEADY",
            // First appearance: nothing to compare against.
            "NO_ACTIVITY,DEVELOPING,NEW",
            "NO_ACTIVITY,STRENGTH,NEW",
            "NO_ACTIVITY,MIXED,NEW",
        }
    )
    void trendComparesProblemLoadAcrossWindows(
        PracticeStatus previous,
        PracticeStatus current,
        PracticeTrend expected
    ) {
        assertThat(PracticeStatusDeriver.trendOf(previous, current)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> NO_ACTIVITY yields STEADY")
    @CsvSource({ "DEVELOPING", "MIXED", "STRENGTH", "NO_ACTIVITY" })
    @DisplayName("a silent window is STEADY, never IMPROVING — inactivity must not read as progress")
    void silenceInTheCurrentWindowIsSteady(PracticeStatus previous) {
        assertThat(PracticeStatusDeriver.trendOf(previous, PracticeStatus.NO_ACTIVITY)).isEqualTo(PracticeTrend.STEADY);
    }
}
