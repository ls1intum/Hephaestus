package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    List<AgentConfig> findByWorkspaceId(Long workspaceId);

    @EntityGraph(attributePaths = { "instanceModel.connection", "workspaceModel.connection" })
    Optional<AgentConfig> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    /**
     * Pessimistic lock on a config row for the executor's concurrency gate.
     * Serializes concurrent executors checking {@code maxConcurrentJobs}.
     * Lock timeout prevents indefinite blocking under contention.
     */
    @WorkspaceAgnostic("ID-based lookup; config ID obtained from workspace-scoped job context")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT c FROM AgentConfig c WHERE c.id = :id")
    Optional<AgentConfig> findByIdForUpdate(@Param("id") Long id);

    /**
     * Workspace-scoped pessimistic lock, for a read whose value is about to be snapshotted and mutated.
     * Without it the before-snapshot and the write are not serialized: two concurrent admin PATCHes both
     * read the same prior state, Hibernate's full-column UPDATE makes the later one silently revert the
     * earlier's field, and the audit trail ends up asserting a transition that never survived — with no
     * row for the write that undid it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT c FROM AgentConfig c WHERE c.id = :id AND c.workspace.id = :workspaceId")
    Optional<AgentConfig> findByIdAndWorkspaceIdForUpdate(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    boolean existsByWorkspaceIdAndEnabledTrue(Long workspaceId);

    /** Deterministic default enabled config (oldest wins) — the mentor fallback when unbound. */
    @EntityGraph(attributePaths = { "instanceModel.connection", "workspaceModel.connection" })
    Optional<AgentConfig> findFirstByWorkspaceIdAndEnabledTrueOrderByIdAsc(Long workspaceId);

    /** Whether any config still binds the given instance-catalog model (#1368) — guards model deletion. */
    boolean existsByInstanceModelId(Long instanceModelId);

    /**
     * Whether any config in {@code workspaceId} still binds the given workspace (BYO) model (#1368) —
     * guards {@code WorkspaceLlmModelService#delete}. Workspace-scoped (unlike
     * {@link #existsByInstanceModelId}, which checks a global model across every tenant): a workspace's
     * own model can only ever be bound within that same workspace, so the predicate is both a tenancy
     * requirement and a correctness one.
     */
    boolean existsByWorkspaceModelIdAndWorkspaceId(Long workspaceModelId, Long workspaceId);
}
