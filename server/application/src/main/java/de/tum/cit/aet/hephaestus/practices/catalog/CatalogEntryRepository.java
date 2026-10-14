package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("The instance catalog has no workspace owner")
public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, CatalogEntry.Id> {
    List<CatalogEntry> findByIdTypeOrderByIdSlugAsc(CatalogEntryType type);
}
