package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeAutomatedAssessmentTest extends BaseUnitTest {

    @Test
    void shouldRejectIncoherentNoneCombinations() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.NONE,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.NONE
            )
        );
    }

    @Test
    void shouldAttemptOnlySupportedLanguageModelAssessment() {
        assertThat(
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ).canAttemptAutomatedAssessment()
        ).isTrue();
        assertThat(
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
            ).canAttemptAutomatedAssessment()
        ).isFalse();
        assertThat(
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.NONE,
                PracticeEvidenceSufficiency.NONE
            ).canAttemptAutomatedAssessment()
        ).isFalse();
    }
}
