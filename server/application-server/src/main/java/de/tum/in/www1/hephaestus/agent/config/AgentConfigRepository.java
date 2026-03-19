package de.tum.in.www1.hephaestus.agent.config;

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
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    List<AgentConfig> findByWorkspaceId(Long workspaceId);

    Optional<AgentConfig> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);

    /**
     * Pessimistic lock on a config row for the executor's concurrency gate.
     * Serializes concurrent executors checking {@code maxConcurrentJobs}.
     * Lock timeout prevents indefinite blocking under contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT c FROM AgentConfig c WHERE c.id = :id")
    Optional<AgentConfig> findByIdForUpdate(@Param("id") Long id);
}
