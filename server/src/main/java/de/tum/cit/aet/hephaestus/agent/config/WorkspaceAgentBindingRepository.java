package de.tum.cit.aet.hephaestus.agent.config;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Access to the per-purpose {@link WorkspaceAgentBinding}s of a workspace (#1368). */
public interface WorkspaceAgentBindingRepository extends JpaRepository<WorkspaceAgentBinding, Long> {
    List<WorkspaceAgentBinding> findByWorkspaceId(Long workspaceId);

    Optional<WorkspaceAgentBinding> findByWorkspaceIdAndPurpose(Long workspaceId, AgentPurpose purpose);

    /** Row-lock the binding for admission's re-resolve/re-price, mirroring the model row lock order. */
    @Query("SELECT b FROM WorkspaceAgentBinding b WHERE b.workspace.id = :workspaceId AND b.purpose = :purpose")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkspaceAgentBinding> findByWorkspaceIdAndPurposeForUpdate(
        @Param("workspaceId") Long workspaceId,
        @Param("purpose") AgentPurpose purpose
    );

    boolean existsByInstanceModelId(Long instanceModelId);

    boolean existsByWorkspaceModelIdAndWorkspaceId(Long workspaceModelId, Long workspaceId);
}
