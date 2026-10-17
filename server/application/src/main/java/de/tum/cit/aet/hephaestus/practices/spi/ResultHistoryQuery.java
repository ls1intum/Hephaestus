package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultHistoryQuery {
    List<ResultHistoryEntry> forDeveloper(long workspaceId, long developerAccountId, Instant since, int limit);

    List<ResultHistoryEntry> forItem(long workspaceId, long itemId, long practiceId, int limit);

    Optional<ResultHistoryEntry> find(long workspaceId, UUID resultId, long viewerAccountId, SourceUsePurpose purpose);

    List<ResultHistoryEntry> forAssignment(long workspaceId, ReviewAssignmentId assignment, int limit);
}
