package de.tum.in.www1.hephaestus.workspace;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, WorkspaceMembership.Id> {
    List<WorkspaceMembership> findByWorkspace_Id(Long workspaceId);
    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
    List<WorkspaceMembership> findAllByWorkspace_IdAndUser_IdIn(Long workspaceId, Collection<Long> userIds);
}
