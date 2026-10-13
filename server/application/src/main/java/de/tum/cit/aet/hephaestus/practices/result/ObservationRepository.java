package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.dto.EvidenceCitationSide;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * The workspace's problems for the admin list, with their result, practice and group in one read. {@code f}
     * selects the results by {@link PracticeResultRepository#OPERATOR_PREDICATES}; its outcomes and severities must
     * be null, because {@code c} selects the problems by their own severity. Newest result first, then display order.
     */
    @Query(
            value = """
                    SELECT c.id AS "id",
                           c.practice_result_id AS "practiceResultId",
                           o.agent_job_id AS "agentJobId",
                           p.slug AS "practiceSlug",
                           p.name AS "practiceName",
                           pa.slug AS "groupSlug",
                           pa.name AS "groupName",
                           pa.icon AS "groupIcon",
                           pa.color AS "groupColor",
                           c.severity AS "severity",
                           c.summary AS "summary",
                           c.citations -> 0 ->> 'sourceKind' AS "sourceKind",
                           c.citations -> 0 ->> 'side' AS "side",
                           c.citations -> 0 ->> 'path' AS "path",
                           CAST(c.citations -> 0 ->> 'startLine' AS integer) AS "startLine",
                           CAST(COALESCE(c.citations -> 0 ->> 'endLine', c.citations -> 0 ->> 'startLine') AS integer)
                               AS "endLine",
                           o.artifact_kind AS "artifactKind",
                           o.artifact_id AS "artifactId",
                           o.about_user_id AS "aboutUserId",
                           evaluated_revision.review_rule_fingerprint AS "practiceRevisionFingerprint",
                           current_revision.review_rule_fingerprint AS "currentPracticeRevisionFingerprint",
                           o.superseded_at AS "supersededAt",
                    """ + PracticeResultRepository.INVALIDATED_AT + " AS \"invalidatedAt\","
                    + PracticeResultRepository.DISPUTED_AT + " AS \"disputedAt\"," + """
                           o.observed_at AS "observedAt"
                    """
                    + OPERATOR_OBSERVATIONS
                    + " ORDER BY o.observed_at DESC, o.id DESC, c.position",
            countQuery = "SELECT count(*) " + OPERATOR_OBSERVATIONS,
            nativeQuery = true)
    Page<OperatorObservationRow> findForWorkspace(
            @Param("workspaceId") Long workspaceId,
            @Param("f") ResultQueryFilter resultFilter,
            @Param("c") ObservationQueryFilter observationFilter,
            Pageable pageable);

    String OPERATOR_OBSERVATIONS = """
        FROM practice_observation c
        JOIN practice_result o ON o.id = c.practice_result_id AND o.workspace_id = c.workspace_id
        JOIN practice p ON p.id = o.practice_id
        LEFT JOIN practice_revision evaluated_revision ON evaluated_revision.id = o.practice_revision_id
        LEFT JOIN practice_revision current_revision ON current_revision.id = p.current_revision_id
        LEFT JOIN practice_group pa ON pa.id = p.practice_group_id
        WHERE c.workspace_id = :workspaceId
          AND (CAST(:#{#c.severityNames()} AS text[]) IS NULL
               OR c.severity = ANY(CAST(:#{#c.severityNames()} AS text[])))
          AND (CAST(:#{#c.practiceResultId()} AS uuid) IS NULL
               OR c.practice_result_id = CAST(:#{#c.practiceResultId()} AS uuid))
        """ + PracticeResultRepository.OPERATOR_PREDICATES;

    interface OperatorObservationRow {
        UUID getId();

        UUID getPracticeResultId();

        UUID getAgentJobId();

        String getPracticeSlug();

        String getPracticeName();

        @Nullable
        String getGroupSlug();

        @Nullable
        String getGroupName();

        @Nullable
        String getGroupIcon();

        @Nullable
        String getGroupColor();

        Severity getSeverity();

        String getSummary();

        @Nullable
        String getSourceKind();

        @Nullable
        EvidenceCitationSide getSide();

        /** The first citation's path; null when it names none. */
        @Nullable
        String getPath();

        @Nullable
        Integer getStartLine();

        @Nullable
        Integer getEndLine();

        /** The raw column: a native-query projection is mapped from JDBC types, with no converter run. */
        String getArtifactKind();

        Long getArtifactId();

        @Nullable
        Long getAboutUserId();

        @Nullable
        String getPracticeRevisionFingerprint();

        @Nullable
        String getCurrentPracticeRevisionFingerprint();

        @Nullable
        Instant getSupersededAt();

        @Nullable
        Instant getInvalidatedAt();

        @Nullable
        Instant getDisputedAt();

        Instant getObservedAt();
    }

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
