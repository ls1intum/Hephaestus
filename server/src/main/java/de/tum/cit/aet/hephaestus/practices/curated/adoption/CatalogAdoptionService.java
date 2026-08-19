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
            throw new StaleCatalogAdoptionPlanException();
        }
        requireCurrentPlan(ifMatch, plan.etag());
        if (plan.availability() != CatalogAdoptionAvailability.AVAILABLE) {
            throw new PracticeSlugConflictException(
                "A practice with slug '" + slug + "' already exists in this workspace."
            );
        }
        if (plan.areaDisposition() == CatalogAreaDisposition.CREATE_CATALOG_AREA) {
            areaService.adoptAreaFromCatalog(context, plan.areaSlug(), plan.areaDefinition(), plan.areaDisplayOrder());
        }
        return practiceService.adoptPracticeFromCatalog(context, slug, plan.definition());
    }

    private static void requireCurrentPlan(String ifMatch, String currentEtag) {
        EntityTagPrecondition precondition;
        try {
            precondition = EntityTagPrecondition.parse(ifMatch);
        } catch (IllegalArgumentException exception) {
            throw new StaleCatalogAdoptionPlanException();
        }
        if (
            precondition
                .candidates()
                .stream()
                .anyMatch(candidate -> candidate.isWildcard()) ||
            !precondition.matches(currentEtag)
        ) {
            throw new StaleCatalogAdoptionPlanException();
        }
    }

    static String formatted(String etag) {
        return EntityTagPrecondition.format(etag);
    }
}
