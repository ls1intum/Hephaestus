package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("The primary key is the owning workspace ID")
public interface PracticeCatalogInstallationRepository extends Repository<PracticeCatalogInstallation, Long> {
    PracticeCatalogInstallation save(PracticeCatalogInstallation installation);

    boolean existsById(Long workspaceId);

    @Query(
        """
        SELECT i.workspaceId
        FROM PracticeCatalogInstallation i
        WHERE i.provenanceLinkedAt IS NULL
        """
    )
    List<Long> findWorkspaceIdsAwaitingProvenanceLink();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PracticeCatalogInstallation i WHERE i.workspaceId = :workspaceId")
    Optional<PracticeCatalogInstallation> findByWorkspaceIdForUpdate(Long workspaceId);
}
