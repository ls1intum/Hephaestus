package de.tum.in.www1.hephaestus.gitprovider.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProjectStatusUpdateRepository extends JpaRepository<ProjectStatusUpdate, Long> {
    Optional<ProjectStatusUpdate> findByNodeId(String nodeId);

    boolean existsByNodeId(String nodeId);

    List<ProjectStatusUpdate> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectStatusUpdate> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Modifying
    @Query("DELETE FROM ProjectStatusUpdate s WHERE s.project.id = :projectId AND s.nodeId NOT IN :nodeIds")
    int deleteByProjectIdAndNodeIdNotIn(@Param("projectId") Long projectId, @Param("nodeIds") List<String> nodeIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO project_status_update (
            id, node_id, project_id, body, start_date, target_date, status, creator_id, created_at, updated_at
        )
        VALUES (
            :id, :nodeId, :projectId, :body, :startDate, :targetDate, :status, :creatorId, :createdAt, :updatedAt
        )
        ON CONFLICT (node_id) DO UPDATE SET
            body = EXCLUDED.body,
            start_date = EXCLUDED.start_date,
            target_date = EXCLUDED.target_date,
            status = EXCLUDED.status,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") Long id,
        @Param("nodeId") String nodeId,
        @Param("projectId") Long projectId,
        @Param("body") String body,
        @Param("startDate") LocalDate startDate,
        @Param("targetDate") LocalDate targetDate,
        @Param("status") String status,
        @Param("creatorId") Long creatorId,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );
}
