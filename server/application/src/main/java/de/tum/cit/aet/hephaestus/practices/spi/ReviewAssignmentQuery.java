package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewAssignmentQuery {
    Optional<ReviewAssignmentSnapshot> find(long workspaceId, ReviewAssignmentId assignment);

    List<ReviewAssignmentId> executable(long workspaceId, UUID reviewId, long itemId);
}
