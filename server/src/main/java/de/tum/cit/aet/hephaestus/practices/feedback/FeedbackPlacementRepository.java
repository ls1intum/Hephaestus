package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable {@link FeedbackPlacement} rows — where/how a {@link Feedback} unit was
 * physically posted (the SUMMARY comment + each INLINE diff note). Saved via JPA {@code save()} (simple
 * UUID PK + {@code @PrePersist}); the recorder guards against double-writes at the {@link Feedback} level.
 *
 * <p>Workspace-agnostic: a placement carries no tenant column — it is scoped through its parent
 * {@link Feedback} (which holds {@code workspace_id}), so callers tenant-scope at the {@code Feedback} level.
 */
@Repository
@WorkspaceAgnostic("FeedbackPlacement is scoped through its parent Feedback's workspace_id, not its own")
public interface FeedbackPlacementRepository extends JpaRepository<FeedbackPlacement, UUID> {
    /** All placements of a unit, e.g. to recover the SUMMARY {@code external_ref} for edit-in-place. */
    List<FeedbackPlacement> findByFeedbackId(UUID feedbackId);
}
