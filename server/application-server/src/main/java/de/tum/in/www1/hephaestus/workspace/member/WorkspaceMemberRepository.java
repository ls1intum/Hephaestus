package de.tum.in.www1.hephaestus.workspace.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMember.Id> {
    List<WorkspaceMember> findAllByWorkspace_Id(Long workspaceId);

    List<WorkspaceMember> findAllByWorkspace_IdAndUser_IdIn(Long workspaceId, Collection<Long> userIds);

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
}
