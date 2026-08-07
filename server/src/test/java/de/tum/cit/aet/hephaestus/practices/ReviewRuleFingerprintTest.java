package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What counts as the same review rule, now that the occasion and its evidence are part of it.
 *
 * <p>The first two claims are ported from {@code PracticeAutomatedReviewPolicyDigestTest}, which held
 * them while the sources sat on the policy. They are the same claims — changing what a review must read
 * makes it a different review, and the order the author happened to write it in does not — asserted of
 * the digest that now carries the sources. Without them the bindings could be dropped from the
 * fingerprint entirely and every test would stay green while every stored fingerprint went stale.
 */
class ReviewRuleFingerprintTest extends BaseUnitTest {

    private static final SourceKind CORE = new SourceKind("scm.pull-request.core");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");

    @Test
    void shouldChangeWhenARequiredSourceChanges() {
        assertThat(fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(CORE)))).isNotEqualTo(
            fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(DIFF)))
        );
    }

    @Test
    void shouldBeStableAcrossDeclarationOrdering() {
        assertThat(fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(DIFF), required(CORE)))).isEqualTo(
            fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(CORE), required(DIFF)))
        );
    }

    /**
     * The stance is what separates "there were no comments" from "we failed to collect the comments", so
     * a rule that changed it reaches a different verdict on the same evidence.
     */
    @Test
    void shouldChangeWhenAStanceChanges() {
        assertThat(fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(CORE), required(DIFF)))).isNotEqualTo(
            fingerprintOf(
                binding(
                    ScmSignals.PULL_REQUEST_OPENED,
                    required(CORE),
                    new PracticeEvidenceRequirement(DIFF, EvidenceStance.CONTEXTUAL)
                )
            )
        );
    }

    @Test
    void shouldChangeWhenTheOccasionChanges() {
        assertThat(fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(CORE)))).isNotEqualTo(
            fingerprintOf(binding(ScmSignals.PULL_REQUEST_MERGED, required(CORE)))
        );
    }

    /** Whether a draft occasions the review decides which work is reviewed at all. */
    @Test
    void shouldChangeWhenDraftsStartOccasioningTheReview() {
        assertThat(fingerprintOf(binding(ScmSignals.PULL_REQUEST_OPENED, required(CORE)))).isNotEqualTo(
            fingerprintOf(new PracticeBinding(List.of(ScmSignals.PULL_REQUEST_OPENED), List.of(required(CORE)), true))
        );
    }

    private static String fingerprintOf(PracticeBinding binding) {
        return ReviewRuleFingerprint.of(
            "describe-the-change",
            "Describe the change",
            List.of(binding),
            "Criteria.",
            null,
            new PracticeAutomatedReviewPolicy(
                new SourceContractVersion("1.0.0"),
                new PracticeAutomatedReview(
                    PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                    PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
                ),
                PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
                List.of(),
                null
            ),
            null
        );
    }

    private static PracticeBinding binding(SignalName signal, PracticeEvidenceRequirement... needs) {
        return PracticeBinding.on(signal, List.of(needs));
    }

    private static PracticeEvidenceRequirement required(SourceKind sourceKind) {
        return new PracticeEvidenceRequirement(sourceKind, EvidenceStance.REQUIRED);
    }
}
