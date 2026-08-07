package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewValidationTest extends BaseUnitTest {

    @Test
    void shouldKeepAuthorDeclarationSeparateFromIndependentValidation() {
        PracticeAutomatedReviewPolicy requirements = requirements();
        PracticeDefinition definition = definition(requirements);

        PracticeAutomatedReviewValidation validation = PracticeAutomatedReviewValidation.authorDeclared(
            "focused-review",
            definition
        );

        assertThat(validation.status()).isEqualTo(PracticeAutomatedReviewValidationStatus.AUTHOR_DECLARED);
        assertThat(validation.policyDigest()).isEqualTo(PracticeAutomatedReviewPolicyDigest.digest(requirements));
        assertThat(validation.reviewRuleFingerprint()).isEqualTo(definition.provenanceFingerprint("focused-review"));
        assertThat(validation.validator()).isNull();
    }

    @Test
    void shouldIgnoreLearnerCopyWhenFingerprintingReviewRules() {
        PracticeDefinition original = definition(requirements());
        PracticeDefinition revised = new PracticeDefinition(
            original.name(),
            original.artifactKind(),
            original.triggerEvents(),
            original.criteria(),
            original.precomputeScript(),
            original.automatedReviewPolicy(),
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
            new PracticeAutomatedReviewValidation(
                PracticeAutomatedReviewValidationStatus.AUTHOR_DECLARED,
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

    private static PracticeAutomatedReviewPolicy requirements() {
        return new PracticeAutomatedReviewPolicy(
            new SourceContractVersion("1.0.0"),
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            List.of(
                new PracticeEvidenceRequirement(
                    new SourceKind("scm.pull-request.diff"),
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceContentRequirement.NO_REQUIREMENT
                )
            ),
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(),
            null
        );
    }

    private static PracticeDefinition definition(PracticeAutomatedReviewPolicy requirements) {
        return new PracticeDefinition(
            "Focused review",
            ArtifactKinds.PULL_REQUEST,
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
