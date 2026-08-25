package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.PracticeService;
import de.tum.cit.aet.hephaestus.practices.PracticeSlugConflictException;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogLock;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogAdoptionService {

    private final CatalogAdoptionPlanAssembler plans;
    private final CuratedCatalogLock catalogLock;
    private final WorkspaceRepository workspaceRepository;
    private final PracticeAreaService areaService;
    private final PracticeService practiceService;

    @Transactional(readOnly = true)
    public List<CatalogPracticeSummaryDTO> list(WorkspaceContext context) {
        return plans.list(context);
    }

    @Transactional(readOnly = true)
    public CatalogAdoptionPlan preview(WorkspaceContext context, String slug) {
        return plans.plan(context, slug);
    }

    @Transactional(readOnly = true)
    public CatalogAreaAdoptionPlan previewArea(WorkspaceContext context, String slug) {
        return plans.areaPlan(context, slug);
    }

    @Transactional
    public Practice adopt(WorkspaceContext context, String slug, String ifMatch) {
        catalogLock.acquire();
        workspaceRepository
            .findByIdForUpdate(context.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", context.slug()));

        CatalogAdoptionPlan plan;
        try {
            plan = plans.plan(context, slug);
        } catch (EntityNotFoundException exception) {
            throw new StaleCatalogAdoptionPlanException(exception);
        }
        requireCurrentPlan(ifMatch, plan.etag());
        if (plan.availability() != CatalogAdoptionAvailability.AVAILABLE) {
            throw new PracticeSlugConflictException(
                "A practice with slug '" + slug + "' already exists in this workspace."
            );
        }
        if (plan.areaDisposition() == CatalogAreaDisposition.CREATE_CATALOG_AREA) {
            areaService.adoptAreaFromCatalog(
                context,
                Objects.requireNonNull(plan.areaSlug()),
                Objects.requireNonNull(plan.areaDefinition()),
                plan.areaDisplayOrder()
            );
        }
        return practiceService.adoptPracticeFromCatalog(context, slug, plan.definition(), plan.initialAutonomy());
    }

    @Transactional
    public CatalogAreaAdoptionResult adoptArea(WorkspaceContext context, String slug, String ifMatch) {
        catalogLock.acquire();
        workspaceRepository
            .findByIdForUpdate(context.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", context.slug()));

        CatalogAreaAdoptionPlan plan;
        try {
            plan = plans.areaPlan(context, slug);
        } catch (EntityNotFoundException exception) {
            throw new StaleCatalogAdoptionPlanException(exception);
        }
        requireCurrentPlan(ifMatch, plan.etag());
        if (plan.disposition() == CatalogAreaDisposition.CREATE_CATALOG_AREA) {
            areaService.adoptAreaFromCatalog(context, slug, plan.definition(), plan.displayOrder());
        }
        List<Practice> added = plan
            .practices()
            .stream()
            .filter(practice -> practice.availability() == CatalogAdoptionAvailability.AVAILABLE)
            .map(practice ->
                practiceService.adoptPracticeFromCatalog(
                    context,
                    practice.slug(),
                    practice.definition(),
                    practice.initialAutonomy()
                )
            )
            .toList();
        List<Practice> moved = new java.util.ArrayList<>();
        int position = 0;
        for (CatalogAreaPracticeActionDTO action : plan.actions()) {
            if (action.action() == CatalogAreaPracticeAction.MOVE_TO_AREA) {
                practiceService.placePractice(context, action.slug(), slug, position++);
                moved.add(practiceService.getPractice(context, action.slug()));
            } else if (action.action() == CatalogAreaPracticeAction.ADD) {
                position++;
            }
        }
        return new CatalogAreaAdoptionResult(added, List.copyOf(moved));
    }

    record CatalogAreaAdoptionResult(List<Practice> added, List<Practice> moved) {}

    private static void requireCurrentPlan(String ifMatch, String currentEtag) {
        EntityTagPrecondition precondition;
        try {
            precondition = EntityTagPrecondition.parse(ifMatch);
        } catch (IllegalArgumentException exception) {
            throw new StaleCatalogAdoptionPlanException(exception);
        }
        if (!precondition.matches(currentEtag)) {
            throw new StaleCatalogAdoptionPlanException();
        }
    }

    static String formatted(String etag) {
        return EntityTagPrecondition.format(etag);
    }
}
