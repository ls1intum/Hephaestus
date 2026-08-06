package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewPolicyTest extends BaseUnitTest {

    private static final SourceContractVersion VERSION = new SourceContractVersion("1.0.0");
    private static final EvidenceProfileId PROFILE = new EvidenceProfileId("pull-request-review");
    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");

    @Test
    void shouldRejectEvidenceWithoutAutomatedReview() {
        assertThatThrownBy(() -> requirements(none(), List.of(required(CORE)), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
        assertThatThrownBy(() ->
            requirements(none(), List.of(), List.of(new PracticeOptionalContextSource(CORE)), List.of())
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
        assertThatThrownBy(() ->
            requirements(
                none(),
                List.of(),
                List.of(),
                List.of(new PracticeEvidenceLimitation("UNSUPPORTED_CLAIM", "Unsupported claim."))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without automated review");
    }

    @Test
    void shouldRejectAutomatedReviewWithoutRequiredEvidence() {
        assertThatThrownBy(() -> requirements(languageModel(), List.of(), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires at least one evidence source");
    }

    @Test
    void shouldRejectDuplicateAndOverlappingSources() {
        assertThatThrownBy(() ->
            requirements(languageModel(), List.of(required(CORE), required(CORE)), List.of(), List.of())
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate source");
        assertThatThrownBy(() ->
            requirements(
                languageModel(),
                List.of(required(CORE)),
                List.of(new PracticeOptionalContextSource(DIFF), new PracticeOptionalContextSource(DIFF)),
                List.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate source");
        assertThatThrownBy(() ->
            requirements(
                languageModel(),
                List.of(required(CORE)),
                List.of(new PracticeOptionalContextSource(CORE)),
                List.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("both required evidence and optional context");
    }

    @Test
    void shouldRejectDuplicateLimitationCodesAndInsufficientEvidenceWithNoReason() {
        PracticeEvidenceLimitation limitation = new PracticeEvidenceLimitation(
            "MISSING_CONTEXT",
            "Context is missing."
        );
        assertThatThrownBy(() ->
            requirements(languageModel(), List.of(required(CORE)), List.of(), List.of(limitation, limitation))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("codes must be unique");
        PracticeAutomatedReview additionalContext = new PracticeAutomatedReview(
            PracticeAutomatedReviewMode.LANGUAGE_MODEL,
            PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
        );
        assertThatThrownBy(() -> requirements(additionalContext, List.of(required(CORE)), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must state why a human is needed");
    }

    @Test
    void shouldCanonicalizeSourceAndLimitationOrder() {
        PracticeAutomatedReviewPolicy requirements = requirements(
            languageModel(),
            List.of(required(DIFF), required(CORE)),
            List.of(
                new PracticeOptionalContextSource(new SourceKind("scm.pull-request.comments")),
                new PracticeOptionalContextSource(new SourceKind("scm.pull-request.approvals"))
            ),
            List.of(
                new PracticeEvidenceLimitation("SECOND_LIMITATION", "Second."),
                new PracticeEvidenceLimitation("FIRST_LIMITATION", "First.")
            )
        );

        assertThat(requirements.requiredEvidence())
            .extracting(item -> item.sourceKind().value())
            .containsExactly("scm.pull-request.core", "scm.pull-request.diff");
        assertThat(requirements.optionalContext())
            .extracting(item -> item.sourceKind().value())
            .containsExactly("scm.pull-request.approvals", "scm.pull-request.comments");
        assertThat(requirements.knownLimitations())
            .extracting(PracticeEvidenceLimitation::code)
            .containsExactly("FIRST_LIMITATION", "SECOND_LIMITATION");
    }

    private static PracticeAutomatedReviewPolicy requirements(
        PracticeAutomatedReview automatedReview,
        List<PracticeEvidenceRequirement> requiredEvidence,
        List<PracticeOptionalContextSource> optionalContext,
        List<PracticeEvidenceLimitation> knownLimitations
    ) {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            PROFILE,
            automatedReview,
            requiredEvidence,
            optionalContext,
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
        return new PracticeEvidenceRequirement(
            sourceKind,
            EvidenceCompletenessRequirement.COMPLETE,
            EvidenceContentRequirement.NO_REQUIREMENT
        );
    }
}
