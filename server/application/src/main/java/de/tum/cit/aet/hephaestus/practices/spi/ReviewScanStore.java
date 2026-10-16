package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewScanStore {
    List<ScanExecutionSnapshot> active(int limit);

    /** Requires the caller's transaction; holds the tenant-scoped review lock. */
    ScanExecutionSnapshot lock(long workspaceId, UUID reviewId);

    List<ReviewAssignmentSnapshot> pending(long workspaceId, UUID reviewId, int limit);

    void inventory(long workspaceId, UUID reviewId, List<Long> presentItemIds, boolean complete);

    void exclusions(long workspaceId, UUID reviewId, List<ScanExcludedItem> exclusions);

    void list(long workspaceId, UUID reviewId, long practiceId, List<ScanListedItem> members, boolean complete);

    Optional<UUID> reuse(long workspaceId, ReviewAssignmentId assignment);
}
