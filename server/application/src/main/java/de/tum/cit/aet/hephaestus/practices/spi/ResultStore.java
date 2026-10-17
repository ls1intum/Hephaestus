package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ResultStore {
    AdmissionReceipt admit(long workspaceId, UUID reviewId, UUID jobId, int attempt, List<ResultCandidate> candidates);

    int endCurrent(long workspaceId, long itemId, ResultEndReason reason, Instant at);

    UUID mark(long workspaceId, UUID resultId, ResultMarkKind kind, long actorId, String reason);

    boolean removeMark(long workspaceId, UUID markId, long actorId, String reason);
}
