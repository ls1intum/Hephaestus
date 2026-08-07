package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The digest of the review frame.
 *
 * <p>What a review reads is no longer part of it — that moved to the bindings and is digested by
 * {@code ReviewRuleFingerprint}, where {@code ReviewRuleFingerprintTest} holds it. What is left here is
 * the frame: the contract version, whether a model runs, what happens when the evidence does not pass,
 * and the claims it can never support.
 */
class PracticeAutomatedReviewPolicyDigestTest extends BaseUnitTest {

    private static final String PINNED_DIGEST = "ab525cda62e5d557e6c65899d711e8375572957f03c0d1ba220b632ae37086c6";

    @Test
    void shouldPinACanonicalDigestForAKnownPolicy() {
        // Fingerprints computed by earlier releases are stored and compared against, so the digest is
        // a wire format. Reordering the fields inside it, or changing how they are framed, silently
        // invalidates every stored fingerprint and marks every past review claim stale — with a green
        // suite, because the other tests here only compare digests to each other. This pins the value.
        String digest = PracticeAutomatedReviewPolicyDigest.digest(policy());

        assertThat(digest).isEqualTo(PINNED_DIGEST);
    }

    @Test
    void shouldIncludeReviewModeAndEvidenceSupport() {
        String baseline = PracticeAutomatedReviewPolicyDigest.digest(
            policy(
                capability(
                    PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                    PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                )
            )
        );

        assertThat(baseline).isNotEqualTo(
            PracticeAutomatedReviewPolicyDigest.digest(
                policy(
                    capability(
                        PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                        PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
                    )
                )
            )
        );
    }

    /**
     * A limitation is a claim the practice will never make, so a review run under a policy that added
     * one is not the same review as one run before it.
     */
    @Test
    void shouldChangeWhenAKnownLimitationChanges() {
        assertThat(PracticeAutomatedReviewPolicyDigest.digest(policy())).isNotEqualTo(
            PracticeAutomatedReviewPolicyDigest.digest(
                new PracticeAutomatedReviewPolicy(
                    new SourceContractVersion("1.0.0"),
                    capability(
                        PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                        PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                    ),
                    PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
                    List.of(new PracticeEvidenceLimitation("RUNTIME_NOT_OBSERVED", "Something else entirely.")),
                    null
                )
            )
        );
    }

    private static PracticeAutomatedReviewPolicy policy() {
        return policy(
            capability(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            )
        );
    }

    private static PracticeAutomatedReviewPolicy policy(PracticeAutomatedReview automatedReview) {
        return new PracticeAutomatedReviewPolicy(
            new SourceContractVersion("1.0.0"),
            automatedReview,
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            List.of(new PracticeEvidenceLimitation("RUNTIME_NOT_OBSERVED", "Runtime behavior is outside scope.")),
            automatedReview.evidenceSufficiency() == PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT
                ? new PracticeEvidenceLimitation("HUMAN_CONTEXT", "A person must review this practice.")
                : null
        );
    }

    private static PracticeAutomatedReview capability(
        PracticeAutomatedReviewMode method,
        PracticeEvidenceSufficiency coverage
    ) {
        return new PracticeAutomatedReview(method, coverage);
    }
}
