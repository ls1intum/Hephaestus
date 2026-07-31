package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("Bundled catalog synchronization state is global")
public interface CuratedCatalogSyncStateRepository extends Repository<CuratedCatalogSyncState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM CuratedCatalogSyncState state WHERE state.source = :source")
    Optional<CuratedCatalogSyncState> findBySourceForUpdate(@Param("source") String source);
}
