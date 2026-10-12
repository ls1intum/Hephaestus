package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The located problems of practice results. Written once, by the admission that writes their result. */
@Repository
public interface ObservationRepository extends JpaRepository<Observation, UUID> {

    /** The problems of these results, in result order and then display order. */
    @Query("""
        SELECT o FROM Observation o
        WHERE o.workspaceId = :workspaceId AND o.practiceResultId IN :practiceResultIds
        ORDER BY o.practiceResultId, o.position
        """)
    List<Observation> findByPracticeResultIds(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultIds") Collection<UUID> practiceResultIds);

    /** These problems, each only within its workspace. */
    @Query("SELECT o FROM Observation o WHERE o.workspaceId = :workspaceId AND o.id IN :ids")
    List<Observation> findAllByIds(@Param("workspaceId") Long workspaceId, @Param("ids") Collection<UUID> ids);

    /**
     * Records one problem of a result admitted in the same transaction. The result's workspace is required, so a
     * problem can only be filed under a result of its own workspace.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO practice_observation (
            id, workspace_id, practice_result_id, position, observation_key, severity, summary, explanation,
            citations, basis, next_step, recurrence_key)
        SELECT :id, r.workspace_id, r.id, :position, :observationKey, :severity, :summary, :explanation,
               CAST(:citations AS jsonb), CAST(:basis AS jsonb), :nextStep, :recurrenceKey
        FROM practice_result r
        WHERE r.id = :practiceResultId AND r.workspace_id = :workspaceId
        """, nativeQuery = true)
    int insert(
            @Param("id") UUID id,
            @Param("workspaceId") Long workspaceId,
            @Param("practiceResultId") UUID practiceResultId,
            @Param("position") int position,
            @Param("observationKey") @Nullable String observationKey,
            @Param("severity") String severity,
            @Param("summary") String summary,
            @Param("explanation") String explanation,
            @Param("citations") String citations,
            @Param("basis") @Nullable String basis,
            @Param("nextStep") @Nullable String nextStep,
            @Param("recurrenceKey") @Nullable String recurrenceKey);
}
