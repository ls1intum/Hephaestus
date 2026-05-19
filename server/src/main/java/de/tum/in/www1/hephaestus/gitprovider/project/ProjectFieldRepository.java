package de.tum.in.www1.hephaestus.gitprovider.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for ProjectField entities.
 */
public interface ProjectFieldRepository extends JpaRepository<ProjectField, String> {
    /**
     * Finds a field by project ID and field name.
     * This is the canonical lookup using the unique constraint fields.
     *
     * @param projectId the project's ID
     * @param name the field name
     * @return the field if found
     */
    Optional<ProjectField> findByProjectIdAndName(Long projectId, String name);

    /**
     * Finds all fields for a given project.
     *
     * @param projectId the project's ID
     * @return list of fields for the project
     */
    List<ProjectField> findAllByProjectId(Long projectId);

    /**
     * Deletes all fields for a project that are not in the given list of IDs.
     * Used during sync to remove fields that no longer exist in GitHub.
     *
     * @param projectId the project's ID
     * @param fieldIds the IDs to keep
     * @return number of deleted fields
     */
    @Modifying
    @Query("DELETE FROM ProjectField f WHERE f.project.id = :projectId AND f.id NOT IN :fieldIds")
    int deleteByProjectIdAndIdNotIn(@Param("projectId") Long projectId, @Param("fieldIds") List<String> fieldIds);

    /**
     * Atomically inserts or updates a project field (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (project_id, name).
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO project_field (
            id, project_id, name, data_type, options, created_at, updated_at
        )
        VALUES (
            :id, :projectId, :name, :dataType, CAST(:options AS jsonb), :createdAt, :updatedAt
        )
        ON CONFLICT (project_id, name) DO UPDATE SET
            data_type = EXCLUDED.data_type,
            options = EXCLUDED.options,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") String id,
        @Param("projectId") Long projectId,
        @Param("name") String name,
        @Param("dataType") String dataType,
        @Param("options") String options,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );
}
