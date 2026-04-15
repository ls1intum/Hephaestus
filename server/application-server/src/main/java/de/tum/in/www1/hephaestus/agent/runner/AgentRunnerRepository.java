package de.tum.in.www1.hephaestus.agent.runner;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRunnerRepository extends JpaRepository<AgentRunner, Long> {
    List<AgentRunner> findByWorkspaceId(Long workspaceId);

    Optional<AgentRunner> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    @WorkspaceAgnostic("ID-based lookup; runner ID obtained from workspace-scoped job context")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT r FROM AgentRunner r WHERE r.id = :id")
    Optional<AgentRunner> findByIdForUpdate(@Param("id") Long id);
}
