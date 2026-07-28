package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Append-only history of practice criteria snapshots (SCD-2). Workspace-agnostic: a revision is scoped
 * through its {@code practice.workspace} relationship, not a direct workspace_id column.
 */
@Repository
@WorkspaceAgnostic("PracticeRevision scoped through practice.workspace relationship")
public interface PracticeRevisionRepository extends JpaRepository<PracticeRevision, Long> {
    /** The current (latest) revision of a practice — the one a new finding pins to, and the basis for the next number. */
    Optional<PracticeRevision> findFirstByPracticeIdOrderByRevisionNumberDesc(Long practiceId);

    /**
     * The latest revision that already existed at {@code asOf} — the criteria snapshot the detector was
     * actually given when its inputs were prepared. Pinning observations to this (rather than "latest at
     * persist time") keeps provenance honest when an admin edits criteria while a detection run is in flight.
     */
    Optional<PracticeRevision> findFirstByPracticeIdAndCreatedAtLessThanEqualOrderByRevisionNumberDesc(
        Long practiceId,
        Instant asOf
    );
}
