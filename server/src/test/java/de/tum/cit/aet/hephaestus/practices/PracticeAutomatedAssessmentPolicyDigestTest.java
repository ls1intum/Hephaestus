package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedAssessmentPolicyDigestTest extends BaseUnitTest {

    @Test
    void shouldBeStableAcrossDeclarationOrdering() {
        PracticeEvidenceRequirement core = requirement("scm.pull-request.core");
        PracticeEvidenceRequirement diff = requirement("scm.pull-request.diff");

        String first = PracticeAutomatedAssessmentPolicyDigest.digest(requirements(List.of(core, diff)));
        String second = PracticeAutomatedAssessmentPolicyDigest.digest(requirements(List.of(diff, core)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldChangeWhenRequiredSourceChanges() {
        String core = PracticeAutomatedAssessmentPolicyDigest.digest(
            requirements(List.of(requirement("scm.pull-request.core")))
        );
        String diff = PracticeAutomatedAssessmentPolicyDigest.digest(
            requirements(List.of(requirement("scm.pull-request.diff")))
        );

        assertThat(core).isNotEqualTo(diff);
    }

    @Test
    void shouldIncludeAssessmentModeAndEvidenceSupport() {
        var required = List.of(requirement("scm.pull-request.diff"));
        String baseline = PracticeAutomatedAssessmentPolicyDigest.digest(
            requirements(
                required,
                capability(
                    PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                    PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                )
            )
        );

        assertThat(baseline).isNotEqualTo(
            PracticeAutomatedAssessmentPolicyDigest.digest(
                requirements(
                    required,
                    capability(
                        PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                        PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
                    )
                )
            )
        );
    }

    private static PracticeAutomatedAssessmentPolicy requirements(List<PracticeEvidenceRequirement> required) {
        return requirements(
            required,
            capability(
                PracticeAutomatedAssessmentMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            )
        );
    }

    private static PracticeAutomatedAssessmentPolicy requirements(
        List<PracticeEvidenceRequirement> required,
        PracticeAutomatedAssessment automatedAssessment
    ) {
        return new PracticeAutomatedAssessmentPolicy(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            automatedAssessment,
            required,
            List.of(),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_ASSESSMENT,
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

    private static PracticeAutomatedAssessment capability(
        PracticeAutomatedAssessmentMode method,
        PracticeEvidenceSufficiency coverage
    ) {
        return new PracticeAutomatedAssessment(method, coverage);
    }
}
