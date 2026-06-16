package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
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
}
