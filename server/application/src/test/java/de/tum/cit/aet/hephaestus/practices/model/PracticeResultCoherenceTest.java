package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeResultCoherenceTest {
    @Test
    void couldNotDetermineIsStillARecordedOutcome() {
        var result = Result.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .reviewId(UUID.randomUUID())
                .practiceId(1)
                .itemId(1)
                .outcome(ResultOutcome.COULD_NOT_DETERMINE)
                .recordedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
        assertThat(result.getOutcome()).isEqualTo(ResultOutcome.COULD_NOT_DETERMINE);
        assertThat(result.getSeverity()).isNull();
        assertThat(result.getEndedAt()).isNull();
    }

    @Test
    void endingAClaimDoesNotRewriteItsVerdict() {
        var at = Instant.parse("2026-08-01T00:00:00Z");
        var result = Result.builder()
                .outcome(ResultOutcome.NOT_MET)
                .severity(Severity.MAJOR)
                .recordedAt(at)
                .build();
        result.end(ResultEndReason.WORK_CHANGED, at.plusSeconds(1));
        assertThat(result.getOutcome()).isEqualTo(ResultOutcome.NOT_MET);
        assertThat(result.getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
        assertThat(result.getRecordedAt()).isEqualTo(at);
    }

    @Test
    void cannotEndAResultBeforeItWasRecorded() {
        var at = Instant.parse("2026-08-01T00:00:00Z");
        var result = Result.builder().recordedAt(at).build();
        assertThatThrownBy(() -> result.end(ResultEndReason.REMOVED, at.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
