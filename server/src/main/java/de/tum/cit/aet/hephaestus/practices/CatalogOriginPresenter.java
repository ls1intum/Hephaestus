package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns practices and areas into their response shapes, adding the two things neither entity knows on its
 * own: where it came from in the instance catalogue, and what autonomy is actually in force for it.
 *
 * <p>Every method takes the workspace id because the autonomy reported is the <em>effective</em> one, resolved
 * once per response rather than once per row.
 */
@Component
@RequiredArgsConstructor
public class CatalogOriginPresenter {

    private final CuratedCatalogService catalogService;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public PracticeDTO present(Long workspaceId, Practice practice) {
        return PracticeDTO.from(
            practice,
            CatalogOrigin.of(practice, catalogService.catalog()),
            defaultAutonomy(workspaceId)
        );
    }

    public PracticeAreaDTO present(Long workspaceId, PracticeArea area) {
        return PracticeAreaDTO.from(
            area,
            CatalogOrigin.of(area, catalogService.catalog()),
            defaultAutonomy(workspaceId)
        );
    }

    public List<PracticeDTO> presentPractices(Long workspaceId, List<Practice> practices) {
        EffectiveCatalog catalog = catalogService.catalog();
        PracticeAutonomy workspaceDefault = defaultAutonomy(workspaceId);
        return practices
            .stream()
            .map(practice -> PracticeDTO.from(practice, CatalogOrigin.of(practice, catalog), workspaceDefault))
            .toList();
    }

    public List<PracticeAreaDTO> presentAreas(Long workspaceId, List<PracticeArea> areas) {
        EffectiveCatalog catalog = catalogService.catalog();
        PracticeAutonomy workspaceDefault = defaultAutonomy(workspaceId);
        return areas
            .stream()
            .map(area -> PracticeAreaDTO.from(area, CatalogOrigin.of(area, catalog), workspaceDefault))
            .toList();
    }

    private PracticeAutonomy defaultAutonomy(Long workspaceId) {
        return workspaceDefaults.forWorkspace(workspaceId).defaultAutonomy();
    }
}
