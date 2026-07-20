package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance model grant allowlist is global (app_admin-owned), not tenant-scoped.")
public interface LlmModelWorkspaceGrantRepository
    extends JpaRepository<LlmModelWorkspaceGrant, LlmModelWorkspaceGrant.Id>
{
    List<LlmModelWorkspaceGrant> findByIdWorkspaceId(Long workspaceId);

    List<LlmModelWorkspaceGrant> findByIdModelId(Long modelId);
}
