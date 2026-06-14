package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable {@link FeedbackPlacement} rows — where/how a {@link Feedback} unit was
 * physically posted (the SUMMARY comment + each INLINE diff note). Saved via JPA {@code save()} (simple
 * UUID PK + {@code @PrePersist}); the recorder guards against double-writes at the {@link Feedback} level.
 */
@Repository
public interface FeedbackPlacementRepository extends JpaRepository<FeedbackPlacement, UUID> {
    /** All placements of a unit, e.g. to recover the SUMMARY {@code external_ref} for edit-in-place. */
    List<FeedbackPlacement> findByFeedbackId(UUID feedbackId);
}
