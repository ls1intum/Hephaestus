package de.tum.in.www1.hephaestus.agent.config;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    List<AgentConfig> findByWorkspaceId(Long workspaceId);

    Optional<AgentConfig> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);
}
