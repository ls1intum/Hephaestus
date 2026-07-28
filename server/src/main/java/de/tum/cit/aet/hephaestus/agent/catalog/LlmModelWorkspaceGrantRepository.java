package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The grant allowlist: which workspaces may use a {@code GRANTED}-visibility instance model.
 *
 * <p>The bypass is declared per method rather than on the interface: a repository-wide one would also
 * have excused the bind-time check, the one read here that must stay pinned to a single workspace.
 */
public interface LlmModelWorkspaceGrantRepository
    extends JpaRepository<LlmModelWorkspaceGrant, LlmModelWorkspaceGrant.Id>
{
    @WorkspaceAgnostic("Instance-admin sharing editor lists all workspaces a catalog model is granted to")
    List<LlmModelWorkspaceGrant> findByIdModelId(Long modelId);

    @WorkspaceAgnostic("Instance-admin catalog list resolves grants for many models across all workspaces")
    List<LlmModelWorkspaceGrant> findByIdModelIdIn(Collection<Long> modelIds);

    boolean existsByIdModelIdAndIdWorkspaceId(Long modelId, Long workspaceId);
}
