package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import java.util.List;

/** Retained job scope. Historical jobs can have no recorded assignments. */
public record ReviewJobInput(long itemId, List<ReviewAssignmentId> assignments) {
    public ReviewJobInput {
        if (itemId <= 0) throw new IllegalArgumentException("Item id must be positive");
        assignments = List.copyOf(assignments);
        if (assignments.stream().anyMatch(assignment -> assignment.itemId() != itemId)) {
            throw new IllegalArgumentException("A job reviews one item");
        }
        if (assignments.stream().distinct().count() != assignments.size()) {
            throw new IllegalArgumentException("A job must not repeat a review assignment");
        }
        if (assignments.stream().map(ReviewAssignmentId::reviewId).distinct().count() > 1) {
            throw new IllegalArgumentException("A job belongs to one review");
        }
    }
}
