package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("The instance catalog is global")
public interface CuratedPracticeOverrideRepository extends Repository<CuratedPracticeOverride, Long> {
    CuratedPracticeOverride save(CuratedPracticeOverride override);

    void delete(CuratedPracticeOverride override);

    List<CuratedPracticeOverride> findAll();

    Optional<CuratedPracticeOverride> findBySlug(String slug);
}
