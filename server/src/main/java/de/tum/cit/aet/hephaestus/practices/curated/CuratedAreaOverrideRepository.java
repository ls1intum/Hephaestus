package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("The instance catalog is global")
public interface CuratedAreaOverrideRepository extends Repository<CuratedAreaOverride, String> {
    CuratedAreaOverride save(CuratedAreaOverride override);

    void delete(CuratedAreaOverride override);

    List<CuratedAreaOverride> findAll();

    Optional<CuratedAreaOverride> findBySlug(String slug);
}
