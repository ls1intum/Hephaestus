package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Fingerprints review-rule inputs, excluding why-it-matters and what-good-looks-like guidance. */
public final class PracticeDetectionFingerprint {

    private PracticeDetectionFingerprint() {}

    public static String of(
        String slug,
        String name,
        WorkArtifact artifactType,
        List<String> triggerEvents,
        String criteria,
        @Nullable String precomputeScript,
        @Nullable String areaSlug
    ) {
        CanonicalDigest digest = new CanonicalDigest()
            .add(slug)
            .add(name)
            .add(artifactType.name())
            .addInt(triggerEvents.size());
        triggerEvents.stream().sorted().forEach(digest::add);
        return digest.add(criteria).addNullable(precomputeScript).addNullable(areaSlug).hex();
    }
}
