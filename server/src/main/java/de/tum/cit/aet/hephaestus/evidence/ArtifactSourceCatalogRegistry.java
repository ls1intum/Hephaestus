package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface ArtifactSourceCatalogRegistry {
    ArtifactSourceCatalog current();

    /** SHA-256 of the exact versioned catalog resource bytes loaded by this runtime. */
    String catalogDigest();

    ArtifactSourceContract requireSource(SourceContractVersion version, SourceKind kind);

    boolean isSourceUsePermitted(SourceContractVersion version, SourceKind kind, SourceUsePurpose purpose);

    /**
     * The sources a review of this artifact kind may observe, refusing a kind no source declares.
     *
     * <p>Refusing rather than returning empty is deliberate: an empty evidence surface and a misspelled
     * artifact kind are indistinguishable to the caller, and the second one must not silently produce a
     * review that looked at nothing.
     */
    Set<SourceKind> requireSourcesFor(SourceContractVersion version, String artifactKind);

    SourceUseDecision requireUseDecision(SourceContractVersion version, String decisionId);

    Optional<Instant> earliestUseDecisionExpiry();
}
