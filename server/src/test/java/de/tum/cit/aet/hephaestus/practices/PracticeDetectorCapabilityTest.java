package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeDetectorCapabilityTest extends BaseUnitTest {

    @Test
    void shouldRejectIncoherentNoneCombinations() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeDetectorCapability(
                PracticeDetectorAssessmentMethod.NONE,
                PracticeDetectorEvidenceCoverage.DECLARED_REQUIREMENTS_SUFFICIENT
            )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeDetectorCapability(
                PracticeDetectorAssessmentMethod.SEMANTIC,
                PracticeDetectorEvidenceCoverage.NONE
            )
        );
    }

    @Test
    void shouldSupportAutomationOnlyWithSufficientCoverage() {
        assertThat(
            new PracticeDetectorCapability(
                PracticeDetectorAssessmentMethod.SEMANTIC,
                PracticeDetectorEvidenceCoverage.DECLARED_REQUIREMENTS_SUFFICIENT
            ).supportsAutomatedDetection()
        ).isTrue();
        assertThat(
            new PracticeDetectorCapability(
                PracticeDetectorAssessmentMethod.SEMANTIC,
                PracticeDetectorEvidenceCoverage.CONDITIONAL
            ).supportsAutomatedDetection()
        ).isFalse();
    }
}
