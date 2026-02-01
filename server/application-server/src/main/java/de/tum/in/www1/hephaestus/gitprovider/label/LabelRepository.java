package de.tum.in.www1.hephaestus.gitprovider.label;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for label entities.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {
    @Query(
        """
        SELECT l
        FROM Label l
        WHERE l.repository.id = :repositoryId
        AND l.name = :name
        """
    )
    Optional<Label> findByRepositoryIdAndName(@Param("repositoryId") Long repositoryId, @Param("name") String name);

    List<Label> findAllByRepository_Id(Long repositoryId);

    /**
     * Atomically inserts a label if absent (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT DO NOTHING to handle concurrent inserts
     * on the unique constraint (repository_id, name). This eliminates the race
     * condition where two threads both check for existence and try to insert.
     *
     * @return 1 if inserted, 0 if duplicate (label with same repo+name exists)
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO label (id, name, color, repository_id)
        VALUES (:id, :name, :color, :repositoryId)
        ON CONFLICT (repository_id, name) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") Long id,
        @Param("name") String name,
        @Param("color") String color,
        @Param("repositoryId") Long repositoryId
    );
}
