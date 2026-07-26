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
     * Locking read for the settings PATCH. There is exactly ONE row for the whole instance and its
     * fields are patched independently, so without serialization two admins writing at the same time
     * each revert the other's field (Hibernate's UPDATE covers every column). That matters most for
     * {@code allow_workspace_connections}: it is a security control, and an admin who turns
     * workspace-supplied providers OFF must not have it turned silently back ON by a concurrent
     * PATCH that only meant to edit the egress allowlist.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InstanceLlmSettings s WHERE s.id = :id")
    Optional<InstanceLlmSettings> findByIdForUpdate(@Param("id") Short id);
}
