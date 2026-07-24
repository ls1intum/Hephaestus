package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("Instance LLM settings singleton is global (app_admin-owned), not tenant-scoped.")
public interface InstanceLlmSettingsRepository extends JpaRepository<InstanceLlmSettings, Short> {}
