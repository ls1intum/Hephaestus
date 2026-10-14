package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.util.UUID;

public interface ResultProofAccess {
    boolean permits(long workspaceId, UUID resultId, SourceUsePurpose purpose);
}
