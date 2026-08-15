package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeAreaService {

    private static final Logger log = LoggerFactory.getLogger(PracticeAreaService.class);

    private final PracticeAreaRepository practiceAreaRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeRevisionService practiceRevisionService;
    private final ConfigAuditPort configAudit;
    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public List<PracticeArea> listAreas(WorkspaceContext ctx, @Nullable Boolean visibleInPracticeDashboardsOnly) {
        return Boolean.TRUE.equals(visibleInPracticeDashboardsOnly)
            ? practiceAreaRepository.findByWorkspaceIdAndVisibleInPracticeDashboardsTrueOrderByDisplayOrderAscNameAsc(
                  ctx.id()
              )
            : practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(ctx.id());
    }

    @Transactional
    public void reorder(WorkspaceContext ctx, List<String> orderedSlugs) {
        if (new HashSet<>(orderedSlugs).size() != orderedSlugs.size()) {
            throw new IllegalArgumentException("orderedSlugs must not contain duplicate slugs");
        }
        lockWorkspace(ctx);
        List<PracticeArea> areas = practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(ctx.id());
        Set<String> existingSlugs = areas.stream().map(PracticeArea::getSlug).collect(Collectors.toSet());
        Set<String> requestedSlugs = new HashSet<>(orderedSlugs);
        if (!existingSlugs.equals(requestedSlugs)) {
            String unknown = requestedSlugs
                .stream()
                .filter(s -> !existingSlugs.contains(s))
                .findFirst()
                .orElse(null);
            if (unknown != null) {
                throw new EntityNotFoundException("PracticeArea", unknown);
            }
            throw new IllegalArgumentException(
                "orderedSlugs must contain every practice area in the workspace (a complete ordering)"
            );
        }
        Map<String, PracticeArea> bySlug = areas.stream().collect(Collectors.toMap(PracticeArea::getSlug, a -> a));
        int order = 0;
        for (String slug : orderedSlugs) {
            PracticeArea area = bySlug.get(slug);
            area.setDisplayOrder(order++);
            practiceAreaRepository.save(area);
        }
    }

    @Transactional(readOnly = true)
    public PracticeArea getArea(WorkspaceContext ctx, String slug) {
        return practiceAreaRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("PracticeArea", slug));
    }

    @Transactional
    public PracticeArea createArea(WorkspaceContext ctx, String slug, AreaAttributes attributes) {
        return createArea(ctx, slug, attributes, true);
    }

    private PracticeArea createArea(WorkspaceContext ctx, String slug, AreaAttributes attributes, boolean recordAudit) {
        if (practiceAreaRepository.existsByWorkspaceIdAndSlug(ctx.id(), slug)) {
            throw new PracticeAreaSlugConflictException(
                "A practice area with slug '" + slug + "' already exists in this workspace."
            );
        }
        Workspace workspace = lockWorkspace(ctx);

        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName(attributes.name());
        area.setDescription(attributes.description());
        area.setDisplayOrder(
            attributes.displayOrder() != null
                ? attributes.displayOrder()
                : practiceAreaRepository.findMaxDisplayOrder(ctx.id()) + 1
        );
        area.setIcon(attributes.icon());
        area.setColor(attributes.color());

        try {
            area = practiceAreaRepository.save(area);
        } catch (DataIntegrityViolationException ex) {
            if (!DataIntegrityViolationConstraints.hasName(ex, "uk_practice_area_workspace_slug")) {
                throw ex;
            }
            throw new PracticeAreaSlugConflictException(
                "A practice area with slug '" + slug + "' already exists in this workspace.",
                ex
            );
        }
        if (recordAudit) {
            configAudit.record(
                ConfigAuditEntry.created(
                    ConfigAuditEntityType.PRACTICE_AREA,
                    area.getId(),
                    ctx.id(),
                    PracticeAreaSnapshot.of(area)
                )
            );
        }
        log.info("Created practice area '{}' (slug={}) in workspace {}", area.getName(), area.getSlug(), ctx.slug());
        return area;
    }

    @Transactional
    public PracticeArea createAreaFromCatalog(
        WorkspaceContext ctx,
        String slug,
        AreaDefinition definition,
        int displayOrder
    ) {
        PracticeArea area = createArea(
            ctx,
            slug,
            new AreaAttributes(
                definition.name(),
                definition.description(),
                displayOrder,
                definition.icon(),
                definition.color()
            ),
            false
        );
        area.setSourceCuratedSlug(slug);
        area.setSourceCuratedFingerprint(definition.provenanceFingerprint(slug));
        return practiceAreaRepository.save(area);
    }

    /**
     * Sets — or clears — the tier this area imposes on the practices under it that hold no tier of their own.
     *
     * <p>The level that makes the chain worth having. An area is the grain a team reasons in, so one write
     * here settles what would otherwise be one write per practice under it. Practices that set their own
     * tier are untouched: they disagreed on purpose, and an area-wide setting is not a reason to overrule
     * them.
     *
     * @param reviewTier the tier to impose, or {@code null} to hold none and follow the workspace default
     */
    @Transactional
    public PracticeArea setReviewTier(WorkspaceContext ctx, String slug, @Nullable PracticeReviewTier reviewTier) {
        lockWorkspace(ctx);
        PracticeArea area = getArea(ctx, slug);
        if (area.getReviewTier() == reviewTier) {
            return area;
        }
        PracticeAreaSnapshot before = PracticeAreaSnapshot.of(area);
        area.setReviewTier(reviewTier);
        area = practiceAreaRepository.save(area);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_AREA,
                area.getId(),
                ctx.id(),
                before,
                PracticeAreaSnapshot.of(area)
            )
        );
        return area;
    }

    @Transactional
    public PracticeArea updateArea(WorkspaceContext ctx, String slug, AreaAttributes attributes) {
        return updateArea(ctx, slug, attributes, null);
    }

    @Transactional
    public PracticeArea updateArea(
        WorkspaceContext ctx,
        String slug,
        AreaAttributes attributes,
        @Nullable Boolean visibleInPracticeDashboards
    ) {
        lockWorkspace(ctx);
        PracticeArea area = getArea(ctx, slug);
        PracticeAreaSnapshot before = PracticeAreaSnapshot.of(area);
        boolean snapshotChanged =
            (attributes.name() != null && !Objects.equals(attributes.name(), area.getName())) ||
            (attributes.description() != null && !Objects.equals(attributes.description(), area.getDescription())) ||
            (attributes.icon() != null && !Objects.equals(attributes.icon(), area.getIcon())) ||
            (attributes.color() != null && !Objects.equals(attributes.color(), area.getColor()));
        if (attributes.name() != null) {
            area.setName(attributes.name());
        }
        if (attributes.description() != null) {
            area.setDescription(attributes.description());
        }
        if (attributes.displayOrder() != null) {
            area.setDisplayOrder(attributes.displayOrder());
        }
        if (attributes.icon() != null) {
            area.setIcon(attributes.icon());
        }
        if (attributes.color() != null) {
            area.setColor(attributes.color());
        }
        if (visibleInPracticeDashboards != null) {
            area.setVisibleInPracticeDashboards(visibleInPracticeDashboards);
        }
        area = practiceAreaRepository.save(area);
        if (snapshotChanged) {
            for (Practice practice : practiceRepository.findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(
                ctx.id(),
                area.getId()
            )) {
                practiceRevisionService.append(practice);
            }
        }
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_AREA,
                area.getId(),
                ctx.id(),
                before,
                PracticeAreaSnapshot.of(area)
            )
        );
        return area;
    }

    @Transactional
    public void deleteArea(WorkspaceContext ctx, String slug) {
        lockWorkspace(ctx);
        PracticeArea area = getArea(ctx, slug);
        PracticeAreaSnapshot areaBefore = PracticeAreaSnapshot.of(area);
        int nextOrder = practiceRepository.findMaxDisplayOrder(ctx.id(), null) + 1;
        for (Practice practice : practiceRepository.findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(
            ctx.id(),
            area.getId()
        )) {
            PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(
                practice,
                practiceRevisionService.currentRevisionNumber(practice)
            );
            practice.setArea(null);
            practice.setDisplayOrder(nextOrder++);
            practiceRepository.save(practice);
            int revisionNumber = practiceRevisionService.append(practice).getRevisionNumber();
            recordPlacementChange(ctx, practice, before, revisionNumber);
        }
        practiceAreaRepository.delete(area);
        configAudit.record(
            ConfigAuditEntry.deleted(ConfigAuditEntityType.PRACTICE_AREA, area.getId(), ctx.id(), areaBefore)
        );
        log.info("Deleted practice area (slug={}) in workspace {}", slug, ctx.slug());
    }

    @Transactional
    public Practice bindPractice(WorkspaceContext ctx, String practiceSlug, @Nullable String areaSlug) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), practiceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", practiceSlug));

        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(
            practice,
            practiceRevisionService.currentRevisionNumber(practice)
        );
        if (!applyBinding(ctx, practice, areaSlug)) {
            return practice;
        }
        practice = practiceRepository.save(practice);
        int revisionNumber = practiceRevisionService.append(practice).getRevisionNumber();
        recordPlacementChange(ctx, practice, before, revisionNumber);
        return practice;
    }

    boolean applyBinding(WorkspaceContext ctx, Practice practice, @Nullable String areaSlug) {
        PracticeArea area =
            areaSlug == null
                ? null
                : practiceAreaRepository
                      .findByWorkspaceIdAndSlug(ctx.id(), areaSlug)
                      .orElseThrow(() -> new EntityNotFoundException("PracticeArea", areaSlug));
        PracticeArea currentArea = practice.getArea();
        if (
            (currentArea == null && area == null) ||
            (currentArea != null && area != null && Objects.equals(currentArea.getId(), area.getId()))
        ) {
            return false;
        }

        int displayOrder = practiceRepository.findMaxDisplayOrder(ctx.id(), area == null ? null : area.getId()) + 1;
        practice.setArea(area);
        practice.setDisplayOrder(displayOrder);
        return true;
    }

    private Workspace lockWorkspace(WorkspaceContext ctx) {
        return workspaceRepository
            .findByIdForUpdate(ctx.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));
    }

    private void recordPlacementChange(
        WorkspaceContext ctx,
        Practice practice,
        PracticeDefinitionSnapshot before,
        int revisionNumber
    ) {
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                ctx.id(),
                before,
                PracticeDefinitionSnapshot.of(practice, revisionNumber)
            )
        );
    }
}
