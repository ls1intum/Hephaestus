package de.tum.in.www1.hephaestus.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRoleAssignmentRepository extends JpaRepository<WorkspaceRoleAssignment, Long> {
    List<WorkspaceRoleAssignment> findByWorkspaceId(Long workspaceId);
    Optional<WorkspaceRoleAssignment> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
