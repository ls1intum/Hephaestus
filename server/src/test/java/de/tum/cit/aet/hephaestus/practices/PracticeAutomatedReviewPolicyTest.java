package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewPolicyTest extends BaseUnitTest {

    private static final SourceContractVersion VERSION = new SourceContractVersion("1.0.0");
    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind COMMENTS = new SourceKind("scm.pull-request.comments");

    @Test
    void shouldRejectEvidenceWithoutAutomatedReview() {
        assertThatThrownBy(() -> requirements(none(), List.of(required(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
        assertThatThrownBy(() -> requirements(none(), List.of(contextual(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
        assertThatThrownBy(() ->
            requirements(none(), List.of(), List.of(new PracticeEvidenceLimitation("UNSUPPORTED_CLAIM", "Claim.")))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
    }

    /**
     * Contextual sources alone are not evidence a review may start on: nothing in the list would ever
     * refuse the run, so the review would proceed having established nothing about what it could see and
     * would still deliver a verdict about a developer.
     */
    @Test
    void shouldRejectAutomatedReviewWithNothingItMustRead() {
        assertThatThrownBy(() -> requirements(languageModel(), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one required evidence source");
        assertThatThrownBy(() -> requirements(languageModel(), List.of(contextual(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one required evidence source");
    }

    /**
     * One list means one uniqueness rule. Naming a source twice used to be legal as long as the two
     * mentions sat in different lists, and the second one silently won.
     */
    @Test
    void shouldRejectASourceNamedTwiceWhateverTheStance() {
        assertThatThrownBy(() -> requirements(languageModel(), List.of(required(CORE), required(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate source");
        assertThatThrownBy(() -> requirements(languageModel(), List.of(required(CORE), contextual(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate source");
    }

    @Test
    void shouldRejectDuplicateLimitationCodesAndInsufficientEvidenceWithNoReason() {
        PracticeEvidenceLimitation limitation = new PracticeEvidenceLimitation(
            "MISSING_CONTEXT",
            "Context is missing."
        );
        assertThatThrownBy(() ->
            requirements(languageModel(), List.of(required(CORE)), List.of(limitation, limitation))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("codes must be unique");
        PracticeAutomatedReview additionalContext = new PracticeAutomatedReview(
            PracticeAutomatedReviewMode.LANGUAGE_MODEL,
            PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
        );
        assertThatThrownBy(() -> requirements(additionalContext, List.of(required(CORE)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must state why a human is needed");
    }

    @Test
    void shouldCanonicalizeSourceAndLimitationOrder() {
        PracticeAutomatedReviewPolicy requirements = requirements(
            languageModel(),
            List.of(required(DIFF), contextual(COMMENTS), required(CORE)),
            List.of(
                new PracticeEvidenceLimitation("SECOND_LIMITATION", "Second."),
                new PracticeEvidenceLimitation("FIRST_LIMITATION", "First.")
            )
        );

        assertThat(requirements.needs())
            .extracting(need -> need.sourceKind().value())
            .containsExactly("scm.pull-request.comments", "scm.pull-request.core", "scm.pull-request.diff");
        assertThat(requirements.requiredNeeds())
            .extracting(need -> need.sourceKind().value())
            .containsExactly("scm.pull-request.core", "scm.pull-request.diff");
        assertThat(requirements.knownLimitations())
            .extracting(PracticeEvidenceLimitation::code)
            .containsExactly("FIRST_LIMITATION", "SECOND_LIMITATION");
    }

    private static PracticeAutomatedReviewPolicy requirements(
        PracticeAutomatedReview automatedReview,
        List<PracticeEvidenceRequirement> needs,
        List<PracticeEvidenceLimitation> knownLimitations
    ) {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            automatedReview,
            needs,
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            knownLimitations,
            null
        );
    }

    private static PracticeAutomatedReview languageModel() {
        return new PracticeAutomatedReview(
            PracticeAutomatedReviewMode.LANGUAGE_MODEL,
            PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
        );
    }

    private static PracticeAutomatedReview none() {
        return new PracticeAutomatedReview(PracticeAutomatedReviewMode.NONE, PracticeEvidenceSufficiency.NONE);
    }

    private static PracticeEvidenceRequirement required(SourceKind sourceKind) {
        return new PracticeEvidenceRequirement(sourceKind, EvidenceStance.REQUIRED);
    }

    private static PracticeEvidenceRequirement contextual(SourceKind sourceKind) {
        return new PracticeEvidenceRequirement(sourceKind, EvidenceStance.CONTEXTUAL);
    }
}
