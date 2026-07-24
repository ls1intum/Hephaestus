package de.tum.cit.aet.hephaestus.agent.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceLlmConnectionRepository extends JpaRepository<WorkspaceLlmConnection, Long> {
    List<WorkspaceLlmConnection> findByWorkspaceId(Long workspaceId);

    Optional<WorkspaceLlmConnection> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Tenancy-safe lookup for a client-supplied id (path variable) — never trust a bare {@code findById}. */
    Optional<WorkspaceLlmConnection> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
