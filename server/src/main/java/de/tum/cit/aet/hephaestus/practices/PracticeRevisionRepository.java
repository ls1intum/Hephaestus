package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("PracticeRevision scoped through practice.workspace relationship")
public interface PracticeRevisionRepository
    extends org.springframework.data.repository.Repository<PracticeRevision, Long>
{
    PracticeRevision save(PracticeRevision revision);

    List<PracticeRevision> findAll();

    Optional<PracticeRevision> findFirstByPracticeIdOrderByRevisionNumberDesc(Long practiceId);

    /**
     * Revisions carrying a definition but no fingerprint — the rows the schema migration created, which
     * SQL could not fingerprint because the hash is defined in Java.
     */
    @Query(
        """
        SELECT r FROM PracticeRevision r
        WHERE r.practice.workspace.id = :workspaceId
          AND r.slug IS NOT NULL
          AND r.detectionFingerprint IS NULL
        ORDER BY r.id
        """
    )
    List<PracticeRevision> findDefinitionRevisionsMissingFingerprint(@Param("workspaceId") Long workspaceId);

    @Modifying
    @Query(
        value = "UPDATE practice_revision SET detection_fingerprint = :fingerprint WHERE id = :revisionId",
        nativeQuery = true
    )
    void setDetectionFingerprint(@Param("revisionId") long revisionId, @Param("fingerprint") String fingerprint);

    /**
     * Returns the definition available at {@code asOf}, so an edit during detection cannot change the
     * recorded provenance.
     */
    Optional<PracticeRevision> findFirstByPracticeIdAndCreatedAtLessThanEqualOrderByRevisionNumberDesc(
        Long practiceId,
        Instant asOf
    );
}
