package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("The instance catalog is global")
public interface CuratedAreaOverrideRepository extends Repository<CuratedAreaOverride, Long> {
    CuratedAreaOverride save(CuratedAreaOverride override);

    void delete(CuratedAreaOverride override);

    List<CuratedAreaOverride> findAll();

    Optional<CuratedAreaOverride> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CuratedAreaOverride o WHERE o.slug = :slug")
    Optional<CuratedAreaOverride> findBySlugForUpdate(@Param("slug") String slug);
}
