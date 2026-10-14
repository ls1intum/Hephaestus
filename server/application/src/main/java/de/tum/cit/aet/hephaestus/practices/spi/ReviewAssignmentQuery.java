package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Optional;

public interface ReviewAssignmentQuery {
    Optional<ReviewAssignmentSnapshot> find(long workspaceId, ReviewAssignmentId assignment);
}
