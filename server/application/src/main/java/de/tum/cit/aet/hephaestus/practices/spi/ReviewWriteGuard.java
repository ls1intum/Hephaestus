package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

public interface ReviewWriteGuard {
    void lock(long workspaceId, UUID reviewId);
}
