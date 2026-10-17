package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.util.UUID;

public interface ResultProofAccess {
    /** Human access to retained history does not certify it for reuse or new delivery. */
    boolean permitsHistory(long workspaceId, UUID resultId, SourceUsePurpose purpose);

    boolean permits(long workspaceId, UUID resultId, SourceUsePurpose purpose);
}
