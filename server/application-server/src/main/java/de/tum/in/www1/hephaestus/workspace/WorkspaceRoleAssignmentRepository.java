package de.tum.in.www1.hephaestus.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRoleAssignmentRepository extends JpaRepository<WorkspaceRoleAssignment, Long> {
    List<WorkspaceRoleAssignment> findByWorkspaceId(Long workspaceId);
    Optional<WorkspaceRoleAssignment> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
