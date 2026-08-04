package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewTest extends BaseUnitTest {

    @Test
    void shouldRejectIncoherentNoneCombinations() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.NONE,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
            new PracticeAutomatedReview(PracticeAutomatedReviewMode.LANGUAGE_MODEL, PracticeEvidenceSufficiency.NONE)
        );
    }

    @Test
    void shouldAttemptOnlySupportedLanguageModelAssessment() {
        assertThat(
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ).canAttemptAutomatedReview()
        ).isTrue();
        assertThat(
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
            ).canAttemptAutomatedReview()
        ).isFalse();
        assertThat(
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.NONE,
                PracticeEvidenceSufficiency.NONE
            ).canAttemptAutomatedReview()
        ).isFalse();
    }
}
