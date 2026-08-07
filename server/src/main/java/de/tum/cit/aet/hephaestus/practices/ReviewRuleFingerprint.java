package de.tum.cit.aet.hephaestus.practices;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Fingerprints review-rule inputs, excluding why-it-matters and what-good-looks-like guidance.
 *
 * <p>The scheme prefix is bumped whenever the <em>inputs</em> change rather than the rules, so a stored
 * fingerprint is never silently compared against one computed from a different set of facts. {@code v3}
 * dropped the named evidence profile: it was the set of sources that declare they apply to the artifact
 * kind, which the kind — already digested — determines on its own.
 *
 * <p>Bindings did not bump it again. They changed what is digested, but the branch that introduced v3
 * is unreleased, so no stored v3 digest was ever computed from the old inputs; bumping would only cost
 * a needless recomputation of fingerprints nobody has.
 */
public final class ReviewRuleFingerprint {

    private static final String SCHEME = "v3:";

    private ReviewRuleFingerprint() {}

    public static String of(
        String slug,
        String name,
        List<PracticeBinding> bindings,
        String criteria,
        @Nullable String precomputeScript,
        PracticeAutomatedReviewPolicy automatedReviewPolicy,
        @Nullable String areaSlug
    ) {
        CanonicalDigest digest = new CanonicalDigest().add(slug).add(name);
        addBindings(digest, bindings);
        return (
            SCHEME +
            digest
                .add(criteria)
                .addNullable(precomputeScript)
                .add(PracticeAutomatedReviewPolicyDigest.digest(automatedReviewPolicy))
                .addNullable(areaSlug)
                .hex()
        );
    }

    /**
     * The artifact kind is not digested separately: every signal name carries it, so digesting it too
     * would only give a rename two places to be recorded.
     */
    static void addBindings(CanonicalDigest digest, List<PracticeBinding> bindings) {
        digest.addInt(bindings.size());
        for (PracticeBinding binding : bindings) {
            digest.addInt(binding.signals().size());
            binding.signals().forEach(signal -> digest.add(signal.value()));
            digest.add(String.valueOf(binding.onDrafts()));
            digest.addInt(binding.needs().size());
            binding.needs().forEach(need -> digest.add(need.sourceKind().value()).add(need.stance().name()));
        }
    }
}
