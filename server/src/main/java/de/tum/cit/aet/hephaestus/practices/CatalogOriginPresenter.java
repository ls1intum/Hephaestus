package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogOriginPresenter {

    private final CuratedCatalogService catalogService;

    public PracticeDTO present(Practice practice) {
        return PracticeDTO.from(practice, CatalogOrigin.of(practice, catalogService.catalog()));
    }

    public PracticeAreaDTO present(PracticeArea area) {
        return PracticeAreaDTO.from(area, CatalogOrigin.of(area, catalogService.catalog()));
    }

    public List<PracticeDTO> presentPractices(List<Practice> practices) {
        EffectiveCatalog catalog = catalogService.catalog();
        return practices
            .stream()
            .map(practice -> PracticeDTO.from(practice, CatalogOrigin.of(practice, catalog)))
            .toList();
    }

    public List<PracticeAreaDTO> presentAreas(List<PracticeArea> areas) {
        EffectiveCatalog catalog = catalogService.catalog();
        return areas
            .stream()
            .map(area -> PracticeAreaDTO.from(area, CatalogOrigin.of(area, catalog)))
            .toList();
    }
}
