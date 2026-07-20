package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance model grant allowlist is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelWorkspaceGrantRepository
    extends JpaRepository<LlmModelWorkspaceGrant, LlmModelWorkspaceGrant.Id>
{
    List<LlmModelWorkspaceGrant> findByIdWorkspaceId(Long workspaceId);

    List<LlmModelWorkspaceGrant> findByIdModelId(Long modelId);

    /** Batched grant lookup for the admin list view. */
    List<LlmModelWorkspaceGrant> findByIdModelIdIn(Collection<Long> modelIds);

    /** Bind-time visibility check: is a {@code GRANTED} model shared with this specific workspace? */
    boolean existsByIdModelIdAndIdWorkspaceId(Long modelId, Long workspaceId);
}
