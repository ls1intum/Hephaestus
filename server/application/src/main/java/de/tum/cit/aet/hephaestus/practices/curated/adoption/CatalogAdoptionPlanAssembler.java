package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewValidation;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CatalogAdoptionPlanAssembler {

    private final CuratedCatalogService catalogService;
    private final PracticeRepository practiceRepository;
    private final PracticeGroupRepository groupRepository;

    List<CatalogPracticeSummaryDTO> list(WorkspaceContext context) {
        EffectiveCatalog catalog = catalogService.catalog();
        Map<String, Practice> workspacePractices = practiceRepository
            .findAllForCatalog(context.id())
            .stream()
            .collect(Collectors.toMap(Practice::getSlug, Function.identity()));
        Map<String, String> groupNames = catalog
            .installableGroups()
            .stream()
            .collect(Collectors.toMap(CatalogEntry::slug, group -> group.effective().name()));
        return catalog
            .installablePractices()
            .stream()
            .map(entry ->
                new CatalogPracticeSummaryDTO(
                    entry.slug(),
                    entry.effective().name(),
                    entry.effective().artifactKind(),
                    entry.effective().whyItMatters(),
                    entry.effective().groupSlug(),
                    groupNames.get(entry.effective().groupSlug()),
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
        String groupSlug = entry.effective().groupSlug();
        if (groupSlug == null) {
            return CatalogAdoptionPlan.create(
                slug,
                entry.effective(),
                availability(slug, existingPractice),
                CatalogGroupDisposition.UNASSIGNED,
                null,
                null,
                -1
            );
        }
        PracticeGroup existingGroup = groupRepository.findByWorkspaceIdAndSlug(context.id(), groupSlug).orElse(null);
        if (existingGroup != null) {
            return CatalogAdoptionPlan.create(
                slug,
                entry.effective(),
                availability(slug, existingPractice),
                CatalogGroupDisposition.REUSE_EXISTING_GROUP,
                groupSlug,
                GroupDefinition.from(existingGroup),
                existingGroup.getDisplayOrder()
            );
        }
        GroupDefinition catalogGroup = catalog
            .installableGroups()
            .stream()
            .filter(group -> group.slug().equals(groupSlug))
            .map(CatalogEntry::effective)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Offered catalog group", groupSlug));
        return CatalogAdoptionPlan.create(
            slug,
            entry.effective(),
            availability(slug, existingPractice),
            CatalogGroupDisposition.CREATE_CATALOG_GROUP,
            groupSlug,
            catalogGroup,
            groupRepository.findMaxDisplayOrder(context.id()) + 1
        );
    }

    CatalogGroupAdoptionPlan groupPlan(WorkspaceContext context, String slug) {
        EffectiveCatalog catalog = catalogService.catalog();
        CatalogEntry<GroupDefinition> entry = catalog
            .installableGroups()
            .stream()
            .filter(candidate -> candidate.slug().equals(slug))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Offered catalog group", slug));
        PracticeGroup existingGroup = groupRepository.findByWorkspaceIdAndSlug(context.id(), slug).orElse(null);
        CatalogGroupDisposition disposition =
            existingGroup == null
                ? CatalogGroupDisposition.CREATE_CATALOG_GROUP
                : CatalogGroupDisposition.REUSE_EXISTING_GROUP;
        GroupDefinition definition = existingGroup == null ? entry.effective() : GroupDefinition.from(existingGroup);
        int displayOrder =
            existingGroup == null
                ? groupRepository.findMaxDisplayOrder(context.id()) + 1
                : existingGroup.getDisplayOrder();
        List<CatalogAdoptionPlan> practices = catalog
            .installablePractices()
            .stream()
            .filter(practice -> slug.equals(practice.effective().groupSlug()))
            .map(practice -> plan(context, practice.slug()))
            .toList();
        List<CatalogGroupPracticeActionDTO> actions = practices
            .stream()
            .map(practice -> new CatalogGroupPracticeActionDTO(practice.slug(), groupAction(context, slug, practice)))
            .toList();
        return CatalogGroupAdoptionPlan.create(slug, definition, disposition, displayOrder, practices, actions);
    }

    private CatalogGroupPracticeAction groupAction(
        WorkspaceContext context,
        String groupSlug,
        CatalogAdoptionPlan plan
    ) {
        if (plan.availability() == CatalogAdoptionAvailability.AVAILABLE) {
            return CatalogGroupPracticeAction.ADD;
        }
        if (plan.availability() == CatalogAdoptionAvailability.SLUG_CONFLICT) {
            return CatalogGroupPracticeAction.BLOCKED;
        }
        Practice existing = practiceRepository.findByWorkspaceIdAndSlug(context.id(), plan.slug()).orElseThrow();
        if (existing.getGroup() == null) {
            return CatalogGroupPracticeAction.MOVE_TO_GROUP;
        }
        return groupSlug.equals(existing.getGroup().getSlug())
            ? CatalogGroupPracticeAction.KEEP
            : CatalogGroupPracticeAction.BLOCKED;
    }

    private static CatalogAdoptionAvailability availability(String slug, @Nullable Practice existingPractice) {
        if (existingPractice == null) {
            return CatalogAdoptionAvailability.AVAILABLE;
        }
        return slug.equals(existingPractice.getSourceCuratedSlug())
            ? CatalogAdoptionAvailability.ADOPTED
            : CatalogAdoptionAvailability.SLUG_CONFLICT;
    }
}
