package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewValidation;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CatalogAdoptionPlanAssembler {

    private final CuratedCatalogService catalogService;
    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository areaRepository;

    List<CatalogPracticeSummaryDTO> list(WorkspaceContext context) {
        EffectiveCatalog catalog = catalogService.catalog();
        Map<String, Practice> workspacePractices = practiceRepository
            .findAllForCatalog(context.id())
            .stream()
            .collect(Collectors.toMap(Practice::getSlug, Function.identity()));
        Map<String, String> areaNames = catalog
            .installableAreas()
            .stream()
            .collect(Collectors.toMap(CatalogEntry::slug, area -> area.effective().name()));
        return catalog
            .installablePractices()
            .stream()
            .map(entry ->
                new CatalogPracticeSummaryDTO(
                    entry.slug(),
                    entry.effective().name(),
                    entry.effective().artifactKind(),
                    entry.effective().whyItMatters(),
                    entry.effective().areaSlug(),
                    areaNames.get(entry.effective().areaSlug()),
                    availability(entry.slug(), workspacePractices.get(entry.slug())),
                    PracticeAutomatedReviewValidation.authorDeclared(entry.slug(), entry.effective())
                )
            )
            .toList();
    }

    CatalogAdoptionPlan plan(WorkspaceContext context, String slug) {
        EffectiveCatalog catalog = catalogService.catalog();
        CatalogEntry<PracticeDefinition> entry = catalog
            .installablePractices()
            .stream()
            .filter(candidate -> candidate.slug().equals(slug))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Offered catalog practice", slug));
        Practice existingPractice = practiceRepository.findByWorkspaceIdAndSlug(context.id(), slug).orElse(null);
        String areaSlug = entry.effective().areaSlug();
        if (areaSlug == null) {
            return CatalogAdoptionPlan.create(
                slug,
                entry.effective(),
                availability(slug, existingPractice),
                CatalogAreaDisposition.UNASSIGNED,
                null,
                null,
                -1
            );
        }
        PracticeArea existingArea = areaRepository.findByWorkspaceIdAndSlug(context.id(), areaSlug).orElse(null);
        if (existingArea != null) {
            return CatalogAdoptionPlan.create(
                slug,
                entry.effective(),
                availability(slug, existingPractice),
                CatalogAreaDisposition.REUSE_EXISTING_AREA,
                areaSlug,
                AreaDefinition.from(existingArea),
                existingArea.getDisplayOrder()
            );
        }
        AreaDefinition catalogArea = catalog
            .installableAreas()
            .stream()
            .filter(area -> area.slug().equals(areaSlug))
            .map(CatalogEntry::effective)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Offered catalog area", areaSlug));
        return CatalogAdoptionPlan.create(
            slug,
            entry.effective(),
            availability(slug, existingPractice),
            CatalogAreaDisposition.CREATE_CATALOG_AREA,
            areaSlug,
            catalogArea,
            areaRepository.findMaxDisplayOrder(context.id()) + 1
        );
    }

    CatalogAreaAdoptionPlan areaPlan(WorkspaceContext context, String slug) {
        EffectiveCatalog catalog = catalogService.catalog();
        CatalogEntry<AreaDefinition> entry = catalog
            .installableAreas()
            .stream()
            .filter(candidate -> candidate.slug().equals(slug))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Offered catalog area", slug));
        PracticeArea existingArea = areaRepository.findByWorkspaceIdAndSlug(context.id(), slug).orElse(null);
        CatalogAreaDisposition disposition =
            existingArea == null
                ? CatalogAreaDisposition.CREATE_CATALOG_AREA
                : CatalogAreaDisposition.REUSE_EXISTING_AREA;
        AreaDefinition definition = existingArea == null ? entry.effective() : AreaDefinition.from(existingArea);
        int displayOrder =
            existingArea == null
                ? areaRepository.findMaxDisplayOrder(context.id()) + 1
                : existingArea.getDisplayOrder();
        List<CatalogAdoptionPlan> practices = catalog
            .installablePractices()
            .stream()
            .filter(practice -> slug.equals(practice.effective().areaSlug()))
            .map(practice -> plan(context, practice.slug()))
            .toList();
        List<CatalogAreaPracticeActionDTO> actions = practices
            .stream()
            .map(practice -> new CatalogAreaPracticeActionDTO(practice.slug(), areaAction(context, slug, practice)))
            .toList();
        return CatalogAreaAdoptionPlan.create(slug, definition, disposition, displayOrder, practices, actions);
    }

    private CatalogAreaPracticeAction areaAction(WorkspaceContext context, String areaSlug, CatalogAdoptionPlan plan) {
        if (plan.availability() == CatalogAdoptionAvailability.AVAILABLE) {
            return CatalogAreaPracticeAction.ADD;
        }
        if (plan.availability() == CatalogAdoptionAvailability.SLUG_CONFLICT) {
            return CatalogAreaPracticeAction.BLOCKED;
        }
        Practice existing = practiceRepository.findByWorkspaceIdAndSlug(context.id(), plan.slug()).orElseThrow();
        if (existing.getArea() == null) {
            return CatalogAreaPracticeAction.MOVE_TO_AREA;
        }
        return areaSlug.equals(existing.getArea().getSlug())
            ? CatalogAreaPracticeAction.KEEP
            : CatalogAreaPracticeAction.BLOCKED;
    }

    private static CatalogAdoptionAvailability availability(String slug, Practice existingPractice) {
        if (existingPractice == null) {
            return CatalogAdoptionAvailability.AVAILABLE;
        }
        return slug.equals(existingPractice.getSourceCuratedSlug())
            ? CatalogAdoptionAvailability.ADOPTED
            : CatalogAdoptionAvailability.SLUG_CONFLICT;
    }
}
