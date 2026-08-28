package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Instance LLM settings singleton is global (app_admin-owned), not tenant-scoped.")
public interface InstanceLlmSettingsRepository extends JpaRepository<InstanceLlmSettings, Short> {
    /**
     * One row, independently patchable fields: without this lock a concurrent PATCH of the egress
     * allowlist silently re-enables workspace-supplied providers another admin just turned off.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InstanceLlmSettings s WHERE s.id = :id")
    Optional<InstanceLlmSettings> findByIdForUpdate(@Param("id") Short id);
}
