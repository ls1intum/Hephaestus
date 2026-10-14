package de.tum.cit.aet.hephaestus.practices.spi;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResultOutcomeTest extends BaseUnitTest {
    @ParameterizedTest
    @EnumSource(ResultOutcome.class)
    void shouldRequireSeverityExactlyWhenNotMet(ResultOutcome outcome) {
        if (outcome == ResultOutcome.NOT_MET) {
            assertThatThrownBy(() -> outcome.validate(null)).isInstanceOf(IllegalArgumentException.class);
            for (Severity severity : Severity.values()) {
                assertThatCode(() -> outcome.validate(severity)).doesNotThrowAnyException();
            }
        } else {
            assertThatCode(() -> outcome.validate(null)).doesNotThrowAnyException();
            for (Severity severity : Severity.values()) {
                assertThatThrownBy(() -> outcome.validate(severity)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
