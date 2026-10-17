package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("The instance catalog has no workspace owner")
public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, CatalogEntry.Id> {
    List<CatalogEntry> findByIdTypeOrderByIdSlugAsc(CatalogEntryType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CatalogEntry entry
           SET entry.position = null, entry.updatedAt = :updatedAt
         WHERE entry.position IS NOT NULL
        """)
    int clearPositions(@Param("updatedAt") Instant updatedAt);
}
