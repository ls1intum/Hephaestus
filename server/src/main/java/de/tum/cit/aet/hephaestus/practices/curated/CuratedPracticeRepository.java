package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Instance-managed curated practice catalog is global")
public interface CuratedPracticeRepository extends JpaRepository<CuratedPractice, Long> {
    @EntityGraph(attributePaths = { "currentRevision", "latestBundledRevision" })
    @Query(
        """
        SELECT p FROM CuratedPractice p
        WHERE (:includeRetired = true OR (
            p.retiredAt IS NULL AND NOT (
                p.syncStatus = de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSyncStatus.SOURCE_REMOVED
                AND p.currentRevision.origin <> de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevisionOrigin.ADMIN
            )
        ))
        ORDER BY p.currentRevision.areaSlug ASC NULLS LAST, p.currentRevision.name ASC
        """
    )
    List<CuratedPractice> findCatalog(@Param("includeRetired") boolean includeRetired);

    @EntityGraph(attributePaths = { "currentRevision", "latestBundledRevision" })
    Optional<CuratedPractice> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CuratedPractice p WHERE p.slug = :slug")
    Optional<CuratedPractice> findBySlugForUpdate(@Param("slug") String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT p FROM CuratedPractice p
        WHERE p.sourceKind = de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSourceKind.BUNDLED
        ORDER BY p.slug
        """
    )
    List<CuratedPractice> findAllBundledForUpdate();

    @EntityGraph(attributePaths = { "currentRevision", "latestBundledRevision" })
    @Query(
        """
        SELECT p FROM CuratedPractice p
        WHERE p.sourceKind = de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSourceKind.BUNDLED
          AND p.retiredAt IS NULL
          AND NOT (
              p.syncStatus = de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeSyncStatus.SOURCE_REMOVED
              AND p.currentRevision.origin <> de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevisionOrigin.ADMIN
          )
        ORDER BY p.currentRevision.areaSlug ASC NULLS LAST, p.currentRevision.name ASC
        """
    )
    List<CuratedPractice> findInstallableBundledPractices();
}
