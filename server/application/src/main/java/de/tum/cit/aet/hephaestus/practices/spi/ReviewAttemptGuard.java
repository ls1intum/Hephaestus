package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;

public interface ReviewAttemptGuard {
    void requireOwner(long workspaceId, UUID jobId, int attempt);
}
