package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewPolicyDigestTest extends BaseUnitTest {

    @Test
    void shouldBeStableAcrossDeclarationOrdering() {
        PracticeEvidenceRequirement core = requirement("scm.pull-request.core");
        PracticeEvidenceRequirement diff = requirement("scm.pull-request.diff");

        String first = PracticeAutomatedReviewPolicyDigest.digest(requirements(List.of(core, diff)));
        String second = PracticeAutomatedReviewPolicyDigest.digest(requirements(List.of(diff, core)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldChangeWhenRequiredSourceChanges() {
        String core = PracticeAutomatedReviewPolicyDigest.digest(
            requirements(List.of(requirement("scm.pull-request.core")))
        );
        String diff = PracticeAutomatedReviewPolicyDigest.digest(
            requirements(List.of(requirement("scm.pull-request.diff")))
        );

        assertThat(core).isNotEqualTo(diff);
    }

    @Test
    void shouldIncludeReviewModeAndEvidenceSupport() {
        var required = List.of(requirement("scm.pull-request.diff"));
        String baseline = PracticeAutomatedReviewPolicyDigest.digest(
            requirements(
                required,
                capability(
                    PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                    PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                )
            )
        );

        assertThat(baseline).isNotEqualTo(
            PracticeAutomatedReviewPolicyDigest.digest(
                requirements(
                    required,
                    capability(
                        PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                        PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
                    )
                )
            )
        );
    }

    private static PracticeAutomatedReviewPolicy requirements(List<PracticeEvidenceRequirement> required) {
        return requirements(
            required,
            capability(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            )
        );
    }

    private static PracticeAutomatedReviewPolicy requirements(
        List<PracticeEvidenceRequirement> required,
        PracticeAutomatedReview automatedReview
    ) {
        return new PracticeAutomatedReviewPolicy(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            automatedReview,
            required,
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(new PracticeEvidenceLimitation("RUNTIME_NOT_OBSERVED", "Runtime behavior is outside scope."))
        );
    }

    private static PracticeEvidenceRequirement requirement(String sourceKind) {
        return new PracticeEvidenceRequirement(
            new SourceKind(sourceKind),
            EvidenceCompletenessRequirement.COMPLETE,
            EvidenceFreshnessRequirement.CURRENT
        );
    }

    private static PracticeAutomatedReview capability(
        PracticeAutomatedReviewMode method,
        PracticeEvidenceSufficiency coverage
    ) {
        return new PracticeAutomatedReview(method, coverage);
    }
}
