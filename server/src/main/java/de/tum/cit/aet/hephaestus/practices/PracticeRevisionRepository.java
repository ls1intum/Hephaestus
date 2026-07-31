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

    @Modifying
    @Query(
        value = """
        UPDATE practice_revision
        SET equivalent_curated_revision_id = :curatedRevisionId,
            detection_fingerprint = :fingerprint
        WHERE id = :revisionId
          AND equivalent_curated_revision_id IS NULL
        """,
        nativeQuery = true
    )
    int linkEquivalentCuratedRevision(
        @Param("revisionId") long revisionId,
        @Param("curatedRevisionId") long curatedRevisionId,
        @Param("fingerprint") String fingerprint
    );

    /**
     * Returns the definition available at {@code asOf}, so an edit during detection cannot change the
     * recorded provenance.
     */
    Optional<PracticeRevision> findFirstByPracticeIdAndCreatedAtLessThanEqualOrderByRevisionNumberDesc(
        Long practiceId,
        Instant asOf
    );
}
