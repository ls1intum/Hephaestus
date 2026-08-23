package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic(
    "Every caller-facing method is keyed by workspace_id; findRecoverable and findExhausted are the " +
        "fleet-wide recovery sweep and are deliberately cross-tenant"
)
public interface FeedbackDispatchRepository extends JpaRepository<FeedbackDispatch, UUID> {
    Optional<FeedbackDispatch> findByDestinationKeyAndWorkspaceId(String destinationKey, Long workspaceId);

    Optional<FeedbackDispatch> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    @Modifying
    @Query(
        value = """
        INSERT INTO feedback_dispatch (
            id, destination_key, workspace_id, agent_job_id, feedback_id, destination, state, body,
            target_external_ref, practice_slugs, write_started, next_attempt_at, attempt_count, created_at, updated_at
        ) SELECT
            :#{#command.id()}, :#{#command.destinationKey()}, :#{#command.workspaceId()}, :#{#command.agentJobId()}, :#{#command.feedbackId()}, :#{#command.destination()}, 'PENDING', :#{#command.body()},
            :#{#command.targetExternalRef()}, CAST(:#{#command.practiceSlugs()} AS jsonb), FALSE, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
          FROM agent_job j
         WHERE j.id = :#{#command.agentJobId()} AND j.workspace_id = :#{#command.workspaceId()}
        ON CONFLICT (destination_key) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(@Param("command") FeedbackDispatchInsert command);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE feedback_dispatch
           SET state = 'CLAIMED', lease_owner = :owner, lease_expires_at = :leaseUntil,
               attempt_count = attempt_count + 1, updated_at = CURRENT_TIMESTAMP
         WHERE id = :id AND workspace_id = :workspaceId AND attempt_count < :maxAttempts
           AND (state IN ('PENDING', 'UNCERTAIN')
                OR (state = 'CLAIMED' AND lease_expires_at < CURRENT_TIMESTAMP))
           AND next_attempt_at <= CURRENT_TIMESTAMP
        """,
        nativeQuery = true
    )
    int claim(
        @Param("id") UUID id,
        @Param("workspaceId") Long workspaceId,
        @Param("owner") String owner,
        @Param("leaseUntil") Instant leaseUntil,
        @Param("maxAttempts") int maxAttempts
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE feedback_dispatch SET write_started = TRUE, updated_at = CURRENT_TIMESTAMP
         WHERE id = :id AND workspace_id = :workspaceId AND state = 'CLAIMED'
           AND lease_owner = :owner AND write_started = FALSE
           AND lease_expires_at > CURRENT_TIMESTAMP
        """,
        nativeQuery = true
    )
    int beginWrite(@Param("id") UUID id, @Param("workspaceId") Long workspaceId, @Param("owner") String owner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE feedback_dispatch SET state = :#{#completion.state()}, delivered_external_ref = :#{#completion.externalRef()},
               lease_owner = NULL, lease_expires_at = NULL, next_attempt_at = :#{#completion.nextAttemptAt()},
               last_error = :#{#completion.error()}, updated_at = CURRENT_TIMESTAMP
         WHERE id = :#{#completion.id()} AND workspace_id = :#{#completion.workspaceId()} AND state = 'CLAIMED' AND lease_owner = :#{#completion.owner()}
        """,
        nativeQuery = true
    )
    int finish(@Param("completion") FeedbackDispatchCompletion completion);

    @Query(
        """
        SELECT d FROM FeedbackDispatch d
        WHERE d.attemptCount < :maxAttempts
          AND d.nextAttemptAt <= :now
          AND (d.state IN (de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.PENDING,
                          de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.UNCERTAIN)
           OR (d.state = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.CLAIMED
               AND d.leaseExpiresAt < :now))
        ORDER BY d.updatedAt ASC
        """
    )
    List<FeedbackDispatch> findRecoverable(
        @Param("now") Instant now,
        @Param("maxAttempts") int maxAttempts,
        Pageable pageable
    );

    @Query(
        """
        SELECT d FROM FeedbackDispatch d
        WHERE d.attemptCount >= :maxAttempts
          AND (d.state IN (de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.PENDING,
                           de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.UNCERTAIN)
            OR (d.state = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState.CLAIMED
                AND d.leaseExpiresAt < :now))
        ORDER BY d.updatedAt ASC
        """
    )
    List<FeedbackDispatch> findExhausted(
        @Param("now") Instant now,
        @Param("maxAttempts") int maxAttempts,
        Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        UPDATE feedback_dispatch
           SET state = 'FAILED', lease_owner = NULL, lease_expires_at = NULL,
               last_error = :error, updated_at = CURRENT_TIMESTAMP
         WHERE id = :id AND workspace_id = :workspaceId
           AND state NOT IN ('SENT', 'SUPPRESSED', 'FAILED')
        """,
        nativeQuery = true
    )
    int fail(@Param("id") UUID id, @Param("workspaceId") Long workspaceId, @Param("error") String error);
}
