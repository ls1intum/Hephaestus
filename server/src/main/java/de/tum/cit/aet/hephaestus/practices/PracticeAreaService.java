package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
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
    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public List<PracticeArea> listAreas(WorkspaceContext ctx, @Nullable Boolean activeOnly) {
        return Boolean.TRUE.equals(activeOnly)
            ? practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(ctx.id())
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
        log.info("Created practice area '{}' (slug={}) in workspace {}", area.getName(), area.getSlug(), ctx.slug());
        return area;
    }

    @Transactional
    public PracticeArea updateArea(WorkspaceContext ctx, String slug, AreaAttributes attributes) {
        if (attributes.displayOrder() != null) {
            lockWorkspace(ctx);
        }
        PracticeArea area = getArea(ctx, slug);
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
        return practiceAreaRepository.save(area);
    }

    @Transactional
    public PracticeArea setActive(WorkspaceContext ctx, String slug, boolean active) {
        PracticeArea area = getArea(ctx, slug);
        area.setActive(active);
        return practiceAreaRepository.save(area);
    }

    @Transactional
    public void deleteArea(WorkspaceContext ctx, String slug) {
        lockWorkspace(ctx);
        PracticeArea area = getArea(ctx, slug);
        int nextOrder = practiceRepository.findMaxDisplayOrder(ctx.id(), null) + 1;
        for (Practice practice : practiceRepository.findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(
            ctx.id(),
            area.getId()
        )) {
            practice.setArea(null);
            practice.setDisplayOrder(nextOrder++);
            practiceRepository.save(practice);
        }
        practiceAreaRepository.delete(area);
        log.info("Deleted practice area (slug={}) in workspace {}", slug, ctx.slug());
    }

    @Transactional
    public Practice bindPractice(WorkspaceContext ctx, String practiceSlug, @Nullable String areaSlug) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), practiceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", practiceSlug));

        if (!applyBinding(ctx, practice, areaSlug)) {
            return practice;
        }
        return practiceRepository.save(practice);
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
}
