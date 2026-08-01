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
public interface CuratedPracticeOverrideRepository extends Repository<CuratedPracticeOverride, Long> {
    CuratedPracticeOverride save(CuratedPracticeOverride override);

    void delete(CuratedPracticeOverride override);

    List<CuratedPracticeOverride> findAll();

    Optional<CuratedPracticeOverride> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CuratedPracticeOverride o WHERE o.slug = :slug")
    Optional<CuratedPracticeOverride> findBySlugForUpdate(@Param("slug") String slug);
}
