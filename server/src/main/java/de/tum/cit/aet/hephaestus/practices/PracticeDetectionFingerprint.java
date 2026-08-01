package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Identity of everything about a practice that reaches a detection run: the criteria and precompute
 * script the sandbox executes, plus the slug, name and area written into the run's practice index.
 * Two practices with the same fingerprint detect identically, which is what makes the fingerprint the
 * test for whether a workspace copy still matches its curated source.
 *
 * <p>Deliberately excluded: {@code whyItMatters} and {@code whatGoodLooksLike}. They are read by
 * people, never by the detector, so editing them leaves a copy equivalent to its source.
 */
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
        CanonicalDigest digest = new CanonicalDigest().add(slug).add(name).add(artifactType.name());
        triggerEvents.stream().sorted().forEach(digest::add);
        return digest.add(criteria).addNullable(precomputeScript).addNullable(areaSlug).hex();
    }
}
