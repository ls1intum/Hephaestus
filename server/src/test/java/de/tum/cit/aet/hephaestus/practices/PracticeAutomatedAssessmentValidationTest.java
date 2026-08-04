package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedAssessmentValidationTest extends BaseUnitTest {

    @Test
    void shouldKeepAuthorDeclarationSeparateFromIndependentValidation() {
        PracticeAutomatedAssessmentPolicy requirements = requirements();
        PracticeDefinition definition = definition(requirements);

        PracticeAutomatedAssessmentValidation validation = PracticeAutomatedAssessmentValidation.authorDeclared(
            "focused-review",
            definition
        );

        assertThat(validation.status()).isEqualTo(PracticeAutomatedAssessmentValidationStatus.AUTHOR_DECLARED);
        assertThat(validation.policyDigest()).isEqualTo(PracticeAutomatedAssessmentPolicyDigest.digest(requirements));
        assertThat(validation.reviewRuleFingerprint()).isEqualTo(definition.provenanceFingerprint("focused-review"));
        assertThat(validation.validator()).isNull();
    }

    @Test
    void shouldIgnoreLearnerCopyWhenFingerprintingReviewRules() {
        PracticeDefinition original = definition(requirements());
        PracticeDefinition revised = new PracticeDefinition(
            original.name(),
            original.artifactType(),
            original.triggerEvents(),
            original.criteria(),
            original.precomputeScript(),
            original.automatedAssessmentPolicy(),
            "Reviews reduce integration risk.",
            original.whatGoodLooksLike(),
            original.areaSlug()
        );

        assertThat(revised.provenanceFingerprint("focused-review")).isEqualTo(
            original.provenanceFingerprint("focused-review")
        );
    }

    @Test
    void shouldRejectSelfCertifiedAuthorValidation() {
        assertThatThrownBy(() ->
            new PracticeAutomatedAssessmentValidation(
                PracticeAutomatedAssessmentValidationStatus.AUTHOR_DECLARED,
                new SourceContractVersion("1.0.0"),
                "0".repeat(64),
                "v2:" + "0".repeat(64),
                "v1:" + "1".repeat(64),
                "author",
                Instant.now(),
                "self-review"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot carry validation provenance");
    }

    private static PracticeAutomatedAssessmentPolicy requirements() {
        return new PracticeAutomatedAssessmentPolicy(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            new PracticeAutomatedAssessment(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            List.of(
                new PracticeEvidenceRequirement(
                    new SourceKind("scm.pull-request.diff"),
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                )
            ),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_ASSESSMENT,
            List.of()
        );
    }

    private static PracticeDefinition definition(PracticeAutomatedAssessmentPolicy requirements) {
        return new PracticeDefinition(
            "Focused review",
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Assess whether the change stays focused.",
            null,
            requirements,
            null,
            null,
            null
        );
    }
}
