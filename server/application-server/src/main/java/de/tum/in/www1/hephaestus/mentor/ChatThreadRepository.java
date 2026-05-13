package de.tum.in.www1.hephaestus.mentor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {
    /**
     * List threads owned by the given user inside the given workspace, newest first.
     * Workspace + owner scoping is enforced at the query layer so no controller can
     * accidentally leak threads across tenants.
     */
    List<ChatThread> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(Long workspaceId, Long userId);

    /**
     * Resolve a thread within a workspace; returns empty when the thread either does not
     * exist or belongs to a different workspace.
     */
    Optional<ChatThread> findByIdAndWorkspaceId(UUID id, Long workspaceId);
}
