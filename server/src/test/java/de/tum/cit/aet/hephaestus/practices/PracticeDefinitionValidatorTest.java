package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeDefinitionValidatorTest extends BaseUnitTest {

    private static final SourceContractVersion VERSION = new SourceContractVersion("1.0.0");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind PARTIAL = new SourceKind("outline.documents");
    private static final SourceKind TIMELESS = new SourceKind("scm.linked-work-items");
    private static final SourceKind FOR_ANOTHER_KIND = new SourceKind("scm.issue.core");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PracticeDefinitionValidator validator = new PracticeDefinitionValidator(
        new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC()),
        PracticeTriggerOptionsFixture.real()
    );

    @Test
    void rejectsDuplicateTriggerEvents() {
        assertThatThrownBy(() ->
            validator.validate(
                definition(
                    List.of("PullRequestCreated", "PullRequestCreated"),
                    null,
                    requirements(
                        new PracticeEvidenceRequirement(
                            DIFF,
                            EvidenceCompletenessRequirement.COMPLETE,
                            EvidenceContentRequirement.NO_REQUIREMENT
                        )
                    )
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Trigger events must not contain duplicates");
    }

    @Test
    void rejectsUnknownEvidenceSource() {
        PracticeAutomatedReviewPolicy requirements = requirements(
            new PracticeEvidenceRequirement(
                new SourceKind("scm.pull-request.unknown"),
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceContentRequirement.NO_REQUIREMENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(requirements)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown source");
    }

    @Test
    void rejectsEvidenceThatCannotExistForTheReviewedKind() {
        PracticeAutomatedReviewPolicy requirements = requirements(
            new PracticeEvidenceRequirement(
                FOR_ANOTHER_KIND,
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceContentRequirement.NO_REQUIREMENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(requirements)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source is not available for the selected work type");
    }

    @Test
    void rejectsImpossibleCompletenessRequirement() {
        PracticeAutomatedReviewPolicy requirements = requirements(
            new PracticeEvidenceRequirement(
                PARTIAL,
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceContentRequirement.NO_REQUIREMENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(requirements)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source cannot satisfy COMPLETE requirements");
    }

    @Test
    void acceptsGuidanceOnlyPracticeWithoutAutomatedInputs() {
        assertThatCode(() ->
            validator.validate(definition(List.of(), null, withoutAutomatedReview()))
        ).doesNotThrowAnyException();
    }

    @Test
    void acceptsHumanReviewPracticeWithoutAutomatedInputs() {
        assertThatCode(() -> validator.validate(definition(List.of(), null, humanReview()))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAutomatedInputsForHumanReviewPractice() {
        assertThatThrownBy(() -> validator.validate(definition(List.of("PullRequestCreated"), null, humanReview())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A practice Hephaestus cannot review cannot define events that start a review");
    }

    @Test
    void rejectsReviewTriggersWithoutAutomatedReview() {
        assertThatThrownBy(() ->
            validator.validate(definition(List.of("PullRequestCreated"), null, withoutAutomatedReview()))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A practice Hephaestus cannot review cannot define events that start a review");
    }

    @Test
    void rejectsPrecomputeScriptWithoutAutomatedReview() {
        assertThatThrownBy(() ->
            validator.validate(definition(List.of(), "export default {}", withoutAutomatedReview()))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A practice Hephaestus cannot review cannot define a precompute script");
    }

    private static PracticeDefinition definition(PracticeAutomatedReviewPolicy requirements) {
        return definition(List.of("PullRequestCreated"), null, requirements);
    }

    private static PracticeDefinition definition(
        List<String> triggerEvents,
        String precomputeScript,
        PracticeAutomatedReviewPolicy requirements
    ) {
        return new PracticeDefinition(
            "Focused review",
            ArtifactKinds.PULL_REQUEST,
            triggerEvents,
            "Assess the review",
            precomputeScript,
            requirements,
            null,
            null,
            null
        );
    }

    private static PracticeAutomatedReviewPolicy requirements(PracticeEvidenceRequirement requirement) {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            List.of(requirement),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            null
        );
    }

    private static PracticeAutomatedReviewPolicy withoutAutomatedReview() {
        return new PracticeAutomatedReviewPolicy(
            VERSION,
            new PracticeAutomatedReview(PracticeAutomatedReviewMode.NONE, PracticeEvidenceSufficiency.NONE),
            List.of(),
            List.of(),
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
            List.of(
                new PracticeEvidenceRequirement(
                    DIFF,
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceContentRequirement.NO_REQUIREMENT
                )
            ),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            new PracticeEvidenceLimitation("HUMAN_CONTEXT", "A person must review this practice.")
        );
    }
}
