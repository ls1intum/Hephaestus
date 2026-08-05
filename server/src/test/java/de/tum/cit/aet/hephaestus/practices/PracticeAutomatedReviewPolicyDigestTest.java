package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeAutomatedReviewPolicyDigestTest extends BaseUnitTest {

    private static final String PINNED_DIGEST = "a35046a27f5597abad1a4010049854a656473f3edf46364ab3c051153932f7bb";

    @Test
    void shouldPinACanonicalDigestForAKnownPolicy() {
        // Fingerprints computed by earlier releases are stored and compared against, so the digest is
        // a wire format. Reordering the fields inside it, or changing how they are framed, silently
        // invalidates every stored fingerprint and marks every past review claim stale — with a green
        // suite, because the other tests here only compare digests to each other. This pins the value.
        String digest = PracticeAutomatedReviewPolicyDigest.digest(
            requirements(List.of(requirement("scm.pull-request.core"), requirement("scm.pull-request.diff")))
        );

        assertThat(digest).isEqualTo(PINNED_DIGEST);
    }

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
            EvidenceFreshnessRequirement.CURRENT,
            EvidenceContentRequirement.NO_REQUIREMENT
        );
    }

    private static PracticeAutomatedReview capability(
        PracticeAutomatedReviewMode method,
        PracticeEvidenceSufficiency coverage
    ) {
        return new PracticeAutomatedReview(method, coverage);
    }
}
