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
 * standing caveats every review of that kind carries.
 *
 * <p>Both answers used to be {@code if}-chains over the three kinds this build happened to have, and both
 * threw for a kind they had not been told about. That was the last place in the practices module where a
 * new domain forced an edit here — and it was not a small one: because {@link #policyFor} is called for
 * <em>every</em> bundled practice, a fourth kind could not even be added to the catalog without this file
 * changing first. The module is not supposed to know which kinds exist, so it no longer answers either
 * question itself:
 *
 * <ul>
 *   <li><b>Which sources a binding starts with</b> is a fact about the sources, and each source contract
 *       now states whether it is part of the starting evidence of the kinds it applies to.</li>
 *   <li><b>What the evidence can never settle</b> is a fact about the domain, and each kind's
 *       {@code ArtifactDescriptor} now states it. The contract validator refuses to start when a kind
 *       calls itself reviewable and names none, so the answer cannot go missing.</li>
 * </ul>
 *
 * <p>An unknown kind still throws rather than falling back to a pull request's requirements — a silently
 * borrowed default would demand a diff of something that has none and refuse every review — but "unknown"
 * now means "no source and no descriptor declares it", which is the same thing a misspelling means.
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
     * <p>Every default is {@code REQUIRED}, which is where all 36 shipped practices already put them.
     * The stance is what separates "there were no comments" from "we failed to collect the comments",
     * and only the first of those is a fact about a developer. How strictly each source must then be
     * captured is not stated here at all: it belongs to the source contract, which every practice
     * agreed with anyway.
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
     * The review frame a practice on this kind starts with: the contract it reads under and the claims
     * that kind of evidence can never support, whatever the occasion.
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
