package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Optional;

public interface ArtifactSourceCatalogRegistry {
    ArtifactSourceCatalog current();

    /** SHA-256 of the exact versioned catalog resource bytes loaded by this runtime. */
    String catalogDigest();

    ArtifactSourceContract requireSource(SourceContractVersion version, SourceKind kind);

    boolean isSourceUsePermitted(SourceContractVersion version, SourceKind kind, SourceUseAudience audience);

    EvidenceProfile requireProfile(SourceContractVersion version, EvidenceProfileId id);

    SourceUseDecision requireUseDecision(SourceContractVersion version, String decisionId);

    Optional<Instant> earliestUseDecisionExpiry();
}
