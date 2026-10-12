package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeResultCoherenceTest extends BaseUnitTest {
    @Test
    void shouldRequireAnOutcomeWhenPersisted() {
        assertThatThrownBy(() ->
                        PracticeResult.builder().scopeKey("test-scope").build().onCreate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outcome is required");
    }

    @Test
    void shouldKeepUndeterminedDistinctFromCaptureFailure() {
        var practiceResult = PracticeResult.builder()
                .scopeKey("test-scope")
                .outcome(Outcome.UNDETERMINED)
                .build();
        practiceResult.onCreate();
        assertThat(practiceResult.getOutcome()).isEqualTo(Outcome.UNDETERMINED);
        assertThat(practiceResult.getSeverity()).isNull();
    }
}
