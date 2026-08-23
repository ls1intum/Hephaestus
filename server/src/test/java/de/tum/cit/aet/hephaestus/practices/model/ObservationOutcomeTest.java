package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the presence × assessment matrix, which is now read from exactly one place. Every consumer — the
 * reflection card's two lists, the trend's outcome vector, the census — derives its answer from this mapping,
 * so a wrong cell here is wrong everywhere at once.
 */
@Tag("unit")
class ObservationOutcomeTest {

    @Test
    @DisplayName("maps every cell of the presence × assessment matrix")
    void shouldMapEveryMatrixCell() {
        assertThat(ObservationOutcome.of(Presence.PRESENT, Assessment.GOOD)).isEqualTo(
            ObservationOutcome.DEMONSTRATED_STRENGTH
        );
        assertThat(ObservationOutcome.of(Presence.PRESENT, Assessment.BAD)).isEqualTo(
            ObservationOutcome.COMMISSION_PROBLEM
        );
        assertThat(ObservationOutcome.of(Presence.ABSENT, Assessment.GOOD)).isEqualTo(
            ObservationOutcome.SAFE_AVOIDANCE
        );
        assertThat(ObservationOutcome.of(Presence.ABSENT, Assessment.BAD)).isEqualTo(ObservationOutcome.OMISSION_GAP);
    }

    @Test
    @DisplayName("collapses both no-verdict presences onto NOT_APPLICABLE")
    void shouldCollapseBothNoVerdictPresences() {
        // Different facts for a reader, but neither is an outcome, so neither may move a trend or a standing.
        assertThat(ObservationOutcome.of(Presence.NOT_APPLICABLE, null)).isEqualTo(ObservationOutcome.NOT_APPLICABLE);
        assertThat(ObservationOutcome.of(Presence.INCONCLUSIVE, null)).isEqualTo(ObservationOutcome.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("rejects a pair the presence/assessment coherence CHECK would reject")
    void shouldRejectIncoherentPairs() {
        assertThatThrownBy(() -> ObservationOutcome.of(Presence.PRESENT, null)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> ObservationOutcome.of(Presence.NOT_APPLICABLE, Assessment.GOOD)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> ObservationOutcome.of(Presence.INCONCLUSIVE, Assessment.BAD)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    @DisplayName("sorts the four applicable outcomes into positive and negative evidence")
    void shouldSortOutcomesByValence() {
        assertThat(ObservationOutcome.DEMONSTRATED_STRENGTH.isPositive()).isTrue();
        assertThat(ObservationOutcome.SAFE_AVOIDANCE.isPositive()).isTrue();
        assertThat(ObservationOutcome.COMMISSION_PROBLEM.isNegative()).isTrue();
        assertThat(ObservationOutcome.OMISSION_GAP.isNegative()).isTrue();
        // The no-verdict outcome is neither, and is the only inapplicable one.
        assertThat(ObservationOutcome.NOT_APPLICABLE.isPositive()).isFalse();
        assertThat(ObservationOutcome.NOT_APPLICABLE.isNegative()).isFalse();
        assertThat(ObservationOutcome.NOT_APPLICABLE.isApplicable()).isFalse();
        assertThat(ObservationOutcome.OMISSION_GAP.isApplicable()).isTrue();
    }

    @Test
    @DisplayName("denies a defect detector the demonstrated strength that would be its own defect")
    void shouldDenyDefectDetectorAnIncoherentStrength() {
        // What would be "present" for a detector practice is the harmful behaviour it hunts, so claiming that
        // as a strength would invert the finding. Its safe avoidance is the opposite case and stands.
        assertThat(ObservationOutcome.DEMONSTRATED_STRENGTH.isCoherentStrengthFor(true)).isFalse();
        assertThat(ObservationOutcome.SAFE_AVOIDANCE.isCoherentStrengthFor(true)).isTrue();

        assertThat(ObservationOutcome.DEMONSTRATED_STRENGTH.isCoherentStrengthFor(false)).isTrue();
        assertThat(ObservationOutcome.SAFE_AVOIDANCE.isCoherentStrengthFor(false)).isTrue();
    }
}
