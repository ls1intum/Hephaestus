package de.tum.in.www1.hephaestus.agent.job;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentJobRepository extends JpaRepository<AgentJob, UUID> {
    Page<AgentJob> findByWorkspaceId(Long workspaceId, Pageable pageable);

    Page<AgentJob> findByWorkspaceIdAndStatus(Long workspaceId, AgentJobStatus status, Pageable pageable);

    Optional<AgentJob> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    Page<AgentJob> findByWorkspaceIdAndConfigId(Long workspaceId, Long configId, Pageable pageable);

    Page<AgentJob> findByWorkspaceIdAndStatusAndConfigId(
        Long workspaceId,
        AgentJobStatus status,
        Long configId,
        Pageable pageable
    );

    long countByConfigIdAndStatusIn(Long configId, Collection<AgentJobStatus> statuses);
}
