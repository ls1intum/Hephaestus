package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.practices.model.Observation;

public interface EvidenceAuthorization {
    boolean permits(long workspaceId, Observation observation, SourceUseAudience audience);
}
