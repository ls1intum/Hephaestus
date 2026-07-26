package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The grant allowlist: which workspaces may use a {@code GRANTED}-visibility instance model.
 *
 * <p>The table carries a {@code workspace_id}, so it is treated as workspace-scoped and the tenancy
 * inspector watches it. Only the two admin-side finders below read across tenants, and they say so
 * per method — a repository-wide bypass would also have excused the bind-time check, which is the one
 * read here that must stay pinned to a single workspace.
 */
public interface LlmModelWorkspaceGrantRepository
    extends JpaRepository<LlmModelWorkspaceGrant, LlmModelWorkspaceGrant.Id>
{
    /** Admin sharing editor: every workspace one model is shared with. */
    @WorkspaceAgnostic("Instance-admin sharing editor lists all workspaces a catalog model is granted to")
    List<LlmModelWorkspaceGrant> findByIdModelId(Long modelId);

    /** Batched grant lookup for the admin list view. */
    @WorkspaceAgnostic("Instance-admin catalog list resolves grants for many models across all workspaces")
    List<LlmModelWorkspaceGrant> findByIdModelIdIn(Collection<Long> modelIds);

    /** Bind-time visibility check: is a {@code GRANTED} model shared with this specific workspace? */
    boolean existsByIdModelIdAndIdWorkspaceId(Long modelId, Long workspaceId);
}
