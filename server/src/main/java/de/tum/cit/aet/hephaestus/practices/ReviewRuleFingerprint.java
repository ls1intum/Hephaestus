package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Fingerprints review-rule inputs, excluding why-it-matters and what-good-looks-like guidance. */
public final class ReviewRuleFingerprint {

    private static final String SCHEME = "v2:";

    private ReviewRuleFingerprint() {}

    public static String of(
        String slug,
        String name,
        ArtifactKind artifactKind,
        List<String> triggerEvents,
        String criteria,
        @Nullable String precomputeScript,
        PracticeAutomatedReviewPolicy automatedReviewPolicy,
        @Nullable String areaSlug
    ) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(slug)
            .add(name)
            .add(artifactKind.value())
            .addInt(triggerEvents.size());
        triggerEvents.stream().sorted().forEach(digest::add);
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
}
