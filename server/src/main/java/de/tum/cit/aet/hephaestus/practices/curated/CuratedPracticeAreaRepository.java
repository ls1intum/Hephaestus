package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Instance-managed curated practice areas are global")
public interface CuratedPracticeAreaRepository extends JpaRepository<CuratedPracticeArea, Long> {
    List<CuratedPracticeArea> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsBySlug(String slug);

    Optional<CuratedPracticeArea> findBySlug(String slug);
}
