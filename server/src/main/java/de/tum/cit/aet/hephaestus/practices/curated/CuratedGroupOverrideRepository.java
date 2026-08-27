package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
@WorkspaceAgnostic("The instance catalog is global")
public interface CuratedGroupOverrideRepository extends Repository<CuratedGroupOverride, String> {
    CuratedGroupOverride save(CuratedGroupOverride override);

    void delete(CuratedGroupOverride override);

    List<CuratedGroupOverride> findAll();

    Optional<CuratedGroupOverride> findBySlug(String slug);
}
