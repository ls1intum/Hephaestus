package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rules that need to see a practice whole.
 *
 * <p>The sources sit on the bindings and the review mode sits on the policy, so only the definition can
 * see both — and it enforces the two evidence rules per binding: a practice that reads nothing it must
 * read when a change merges is not saved by reading something when the change was opened.
 */
class PracticeDefinitionTest extends BaseUnitTest {

    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind COMMENTS = new SourceKind("scm.pull-request.comments");

    @Test
    void shouldRejectEvidenceWithoutAutomatedReview() {
        assertThatThrownBy(() ->
            definition(none(), List.of(PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(required(CORE)))))
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
    void shouldRejectAnOccasionWithNothingTheReviewMustRead() {
        assertThatThrownBy(() ->
            definition(
                languageModel(),
                List.of(PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(contextual(CORE))))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one required evidence source");
        assertThatThrownBy(() ->
            definition(languageModel(), List.of(PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of())))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one required evidence source");
    }

    /** Asked of every binding, so a second occasion cannot arrive reading nothing it must read. */
    @Test
    void shouldRejectASecondOccasionWithNothingTheReviewMustRead() {
        assertThatThrownBy(() ->
            definition(
                languageModel(),
                List.of(
                    PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(required(CORE))),
                    PracticeBinding.on(ScmSignals.PULL_REQUEST_MERGED, List.of(contextual(COMMENTS)))
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one required evidence source");
    }

    /**
     * Two bindings on one signal would have to be merged by whoever read them, and the two candidate
     * merges — union the evidence, or take the first — are not the same review.
     */
    @Test
    void shouldRejectASignalBoundTwice() {
        assertThatThrownBy(() ->
            definition(
                languageModel(),
                List.of(
                    PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(required(CORE))),
                    PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(required(COMMENTS)))
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is bound twice");
    }

    @Test
    void shouldRefuseAPracticeThatNamesNoOccasion() {
        assertThatThrownBy(() -> definition(languageModel(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one binding");
    }

    @Test
    void shouldReadTheArtifactKindOffTheBindings() {
        PracticeDefinition definition = definition(
            languageModel(),
            List.of(PracticeBinding.on(ScmSignals.PULL_REQUEST_MERGED, List.of(required(CORE))))
        );

        assertThat(definition.artifactKind()).isEqualTo(ArtifactKinds.PULL_REQUEST);
    }

    private static PracticeDefinition definition(
        PracticeAutomatedReview automatedReview,
        List<PracticeBinding> bindings
    ) {
        return new PracticeDefinition(
            "Describe the change",
            bindings,
            "Criteria.",
            null,
            new PracticeAutomatedReviewPolicy(
                new SourceContractVersion("1.0.0"),
                automatedReview,
                PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
                List.of(),
                null
            ),
            null,
            null,
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
