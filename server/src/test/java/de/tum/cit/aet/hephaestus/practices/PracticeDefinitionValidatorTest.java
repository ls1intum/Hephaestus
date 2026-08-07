package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the validator checks that the definition record cannot check for itself: agreement with the
 * registered domains and with the source catalog.
 *
 * <p>Two rules this file used to hold are gone rather than moved. "Trigger events must not contain
 * duplicates" is now {@code PracticeDefinition}'s "signal is bound twice", asserted where it lives. And
 * a practice only a human reviews may now name the occasion it is about: the occasion is where its
 * artifact kind comes from, so forbidding it left such a practice with nothing to say what it was about
 * — and saying what a practice concerns was never the same claim as asking Hephaestus to act on it.
 */
class PracticeDefinitionValidatorTest extends BaseUnitTest {

    private static final SourceContractVersion VERSION = new SourceContractVersion("1.0.0");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind FOR_ANOTHER_KIND = new SourceKind("scm.issue.core");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PracticeDefinitionValidator validator = new PracticeDefinitionValidator(
        new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC()),
        PracticeSignalOptionsFixture.real()
    );

    /**
     * The cross-check that keeps a derived artifact kind honest: the kind is read off the signal's
     * prefix, so a misspelled signal would otherwise invent a kind nothing can raise and leave the
     * practice sitting in the catalog looking configured and never firing.
     */
    @Test
    void rejectsASignalNoRegisteredDomainDeclares() {
        assertThatThrownBy(() ->
            validator.validate(
                definition(SignalName.of("scm.pull_request.rebased"), null, List.of(need(DIFF)), languageModel())
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Choose signals declared for the selected work type");
    }

    @Test
    void rejectsUnknownEvidenceSource() {
        assertThatThrownBy(() ->
            validator.validate(
                definition(
                    ScmSignals.PULL_REQUEST_OPENED,
                    null,
                    List.of(need(new SourceKind("scm.pull-request.unknown"))),
                    languageModel()
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown source");
    }

    @Test
    void rejectsEvidenceThatCannotExistForTheReviewedKind() {
        assertThatThrownBy(() ->
            validator.validate(
                definition(ScmSignals.PULL_REQUEST_OPENED, null, List.of(need(FOR_ANOTHER_KIND)), languageModel())
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source is not available for the selected work type");
    }

    /** Checked per binding, so a second occasion cannot quietly read something the kind cannot have. */
    @Test
    void rejectsEvidenceThatCannotExistOnASecondOccasion() {
        PracticeDefinition definition = new PracticeDefinition(
            "Focused review",
            List.of(
                PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(need(DIFF))),
                PracticeBinding.on(ScmSignals.PULL_REQUEST_MERGED, List.of(need(FOR_ANOTHER_KIND)))
            ),
            "Assess the review",
            null,
            languageModel(),
            null,
            null,
            null
        );

        assertThatThrownBy(() -> validator.validate(definition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source is not available for the selected work type");
    }

    @Test
    void acceptsGuidanceOnlyPracticeWithoutAutomatedInputs() {
        assertThatCode(() ->
            validator.validate(definition(ScmSignals.PULL_REQUEST_OPENED, null, List.of(), withoutAutomatedReview()))
        ).doesNotThrowAnyException();
    }

    @Test
    void acceptsHumanReviewPracticeThatStillSaysWhatItIsAbout() {
        assertThatCode(() ->
            validator.validate(definition(ScmSignals.PULL_REQUEST_OPENED, null, List.of(need(DIFF)), humanReview()))
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsPrecomputeScriptWithoutAutomatedReview() {
        assertThatThrownBy(() ->
            validator.validate(
                definition(ScmSignals.PULL_REQUEST_OPENED, "export default {}", List.of(), withoutAutomatedReview())
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A practice Hephaestus cannot review cannot define a precompute script");
    }

    @Test
    void rejectsDetectorVocabularyInDeveloperFacingGuidance() {
        PracticeDefinition definition = new PracticeDefinition(
            "Focused review",
            List.of(PracticeBinding.on(ScmSignals.PULL_REQUEST_OPENED, List.of(need(DIFF)))),
            "Assess the review",
            null,
            languageModel(),
            "A description that is ABSENT tells a reviewer nothing.",
            null,
            null
        );

        assertThatThrownBy(() -> validator.validate(definition))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not use detector result labels");
    }

    private static PracticeDefinition definition(
        SignalName signal,
        @Nullable String precomputeScript,
        List<PracticeEvidenceRequirement> needs,
        PracticeAutomatedReviewPolicy policy
    ) {
        return new PracticeDefinition(
            "Focused review",
            List.of(PracticeBinding.on(signal, needs)),
            "Assess the review",
            precomputeScript,
            policy,
            null,
            null,
            null
        );
    }

    private static PracticeEvidenceRequirement need(SourceKind sourceKind) {
        return new PracticeEvidenceRequirement(sourceKind, EvidenceStance.REQUIRED);
    }

    private static PracticeAutomatedReviewPolicy languageModel() {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            null
        );
    }

    private static PracticeAutomatedReviewPolicy withoutAutomatedReview() {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            new PracticeAutomatedReview(PracticeAutomatedReviewMode.NONE, PracticeEvidenceSufficiency.NONE),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            null
        );
    }

    private static PracticeAutomatedReviewPolicy humanReview() {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
            ),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            new PracticeEvidenceLimitation("HUMAN_CONTEXT", "A person must review this practice.")
        );
    }
}
