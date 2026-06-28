package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link PracticeArea}s — the configurable learning-objective grouping over
 * practices — and binding practices to them.
 *
 * <p>An area is a read-model/organizing concept: it groups practices for the dashboards and never
 * participates in detection. The 1:N binding ({@link Practice#getArea()}) keeps the per-area
 * acted-on/total progress denominator unambiguous.
 */
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

    /**
     * Sets each area's {@code displayOrder} to its index in the given list — one atomic write of the
     * whole ordering, so a mid-list failure can't leave duplicate/garbled order values. Every slug must
     * belong to the workspace (a stale/foreign slug is a 404) and must be unique (a duplicate slug would
     * silently assign two indices to one area and is rejected up front).
     */
    @Transactional
    public void reorder(WorkspaceContext ctx, List<String> orderedSlugs) {
        if (new HashSet<>(orderedSlugs).size() != orderedSlugs.size()) {
            throw new IllegalArgumentException("orderedSlugs must not contain duplicate slugs");
        }
        int order = 0;
        for (String slug : orderedSlugs) {
            PracticeArea area = practiceAreaRepository
                .findByWorkspaceIdAndSlug(ctx.id(), slug)
                .orElseThrow(() -> new EntityNotFoundException("PracticeArea", slug));
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
        Workspace workspace = workspaceRepository
            .findById(ctx.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));

        PracticeArea area = new PracticeArea();
        area.setWorkspace(workspace);
        area.setSlug(slug);
        area.setName(attributes.name());
        area.setDescription(attributes.description());
        area.setDisplayOrder(attributes.displayOrder() != null ? attributes.displayOrder() : 0);
        area.setIcon(attributes.icon());
        area.setColor(attributes.color());

        try {
            area = practiceAreaRepository.save(area);
        } catch (DataIntegrityViolationException ex) {
            // Safety net for a concurrent create with the same slug.
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

    /** Deletes an area. Bound practices are unbound (their {@code practice_area_id} is SET NULL by the FK). */
    @Transactional
    public void deleteArea(WorkspaceContext ctx, String slug) {
        PracticeArea area = getArea(ctx, slug);
        practiceAreaRepository.delete(area);
        log.info("Deleted practice area (slug={}) in workspace {}", slug, ctx.slug());
    }

    /**
     * Binds a practice to an area, or unbinds it when {@code areaSlug} is {@code null}. Both the practice
     * and the area are resolved within {@code ctx}'s workspace, so a practice can never be bound to
     * another workspace's area.
     */
    @Transactional
    public Practice bindPractice(WorkspaceContext ctx, String practiceSlug, @Nullable String areaSlug) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), practiceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", practiceSlug));

        if (areaSlug == null) {
            practice.setArea(null);
        } else {
            PracticeArea area = practiceAreaRepository
                .findByWorkspaceIdAndSlug(ctx.id(), areaSlug)
                .orElseThrow(() -> new EntityNotFoundException("PracticeArea", areaSlug));
            practice.setArea(area);
        }
        return practiceRepository.save(practice);
    }
}
