package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewLimitation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The frame a practice on an artifact kind starts with: the evidence a binding reads by default, and the
 * review limitations every review of that kind carries.
 *
 * <p>Both answers are looked up rather than listed here, so that a kind becomes authorable by being
 * declared rather than by an edit to this file — the source contracts say which sources are a kind's
 * starting evidence, and each kind's {@code ArtifactDescriptor} states what its evidence can never
 * settle.
 *
 * <p>An unknown kind throws rather than borrowing a pull request's requirements, which would demand a
 * diff of something that has none and so refuse every review.
 */
@Component
public class PracticeEvidenceDefaults {

    private final ArtifactSourceCatalogRegistry catalogs;
    private final ArtifactCatalog artifacts;

    public PracticeEvidenceDefaults(ArtifactSourceCatalogRegistry catalogs, ArtifactCatalog artifacts) {
        this.catalogs = catalogs;
        this.artifacts = artifacts;
    }

    /**
     * The evidence a binding on this kind starts with when the author has not said otherwise.
     *
     * <p>Every default is {@code REQUIRED}: the stance is what separates "there were no comments" from
     * "we failed to collect the comments", and only the first is a fact about a developer.
     */
    public List<PracticeEvidenceRequirement> needsFor(ArtifactKind artifact) {
        List<SourceKind> defaults = catalogs.requireDefaultSourcesFor(catalogs.current().version(), artifact.value());
        if (defaults.isEmpty()) {
            throw new IllegalArgumentException("No evidence source is a default for artifact kind: " + artifact);
        }
        return defaults
            .stream()
            .map(kind -> new PracticeEvidenceRequirement(kind, EvidenceStance.REQUIRED))
            .toList();
    }

    /**
     * The review frame a practice on this kind starts with: the source-catalog version it reads under
     * and the claims that kind of evidence can never support, whatever the occasion.
     */
    public PracticeAutomatedReviewPolicy policyFor(ArtifactKind artifact) {
        List<ReviewLimitation> limitations = artifacts
            .descriptorFor(artifact)
            .map(ArtifactDescriptor::reviewLimitations)
            .orElseThrow(() ->
                new IllegalArgumentException("No registered domain declares artifact kind: " + artifact)
            );
        if (limitations.isEmpty()) {
            throw new IllegalArgumentException("No review limitations are declared for artifact kind: " + artifact);
        }
        return new PracticeAutomatedReviewPolicy(
            catalogs.current().version(),
            new PracticeAutomatedReview(
                PracticeAutomatedReviewMode.LANGUAGE_MODEL,
                PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
            ),
            PracticeInsufficientEvidenceAction.SKIP_AUTOMATED_REVIEW,
            limitations
                .stream()
                .map(limitation -> new PracticeEvidenceLimitation(limitation.code(), limitation.description()))
                .toList(),
            null
        );
    }
}
