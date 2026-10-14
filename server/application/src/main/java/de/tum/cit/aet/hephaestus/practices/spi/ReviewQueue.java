package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.UUID;

public interface ReviewQueue {
    UUID enqueue(ReviewSnapshot review, long itemId, List<ReviewAssignmentId> assignments);
}
