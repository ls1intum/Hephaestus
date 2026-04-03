package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.in.www1.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing practice definitions (CRUD).
 *
 * <p>This service handles the lifecycle of {@link Practice} entities — creating, reading,
 * updating, and toggling active state.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeRepository practiceRepository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public List<Practice> listPractices(WorkspaceContext ctx, String category, Boolean active) {
        log.debug("Listing practices for workspace {} (category={}, active={})", ctx.slug(), category, active);
        return practiceRepository.findByFilters(ctx.id(), category, active);
    }

    @Transactional(readOnly = true)
    public Practice getPractice(WorkspaceContext ctx, String slug) {
        return practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));
    }

    @Transactional
    public Practice createPractice(WorkspaceContext ctx, CreatePracticeRequestDTO request) {
        if (practiceRepository.existsByWorkspaceIdAndSlug(ctx.id(), request.slug())) {
            throw new PracticeSlugConflictException(
                "A practice with slug '" + request.slug() + "' already exists in this workspace."
            );
        }

        Workspace workspace = workspaceRepository
            .findById(ctx.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));

        Practice practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug(request.slug());
        practice.setName(request.name());
        practice.setCategory(request.category());
        practice.setDescription(request.description());
        practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(request.triggerEvents()));
        practice.setCriteria(request.criteria());

        try {
            practice = practiceRepository.save(practice);
        } catch (DataIntegrityViolationException ex) {
            // Safety net for race condition: concurrent create with same slug
            throw new PracticeSlugConflictException(
                "A practice with slug '" + request.slug() + "' already exists in this workspace.",
                ex
            );
        }

        log.info("Created practice '{}' (slug={}) in workspace {}", practice.getName(), practice.getSlug(), ctx.slug());
        return practice;
    }

    @Transactional
    public Practice updatePractice(WorkspaceContext ctx, String slug, UpdatePracticeRequestDTO request) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        boolean changed = false;
        if (request.name() != null) {
            practice.setName(request.name());
            changed = true;
        }
        if (request.category() != null) {
            practice.setCategory(request.category());
            changed = true;
        }
        if (request.description() != null) {
            practice.setDescription(request.description());
            changed = true;
        }
        if (request.triggerEvents() != null) {
            practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(request.triggerEvents()));
            changed = true;
        }
        if (request.criteria() != null) {
            practice.setCriteria(request.criteria());
            changed = true;
        }

        if (!changed) {
            return practice;
        }

        practice = practiceRepository.save(practice);
        log.info("Updated practice '{}' (slug={}) in workspace {}", practice.getName(), slug, ctx.slug());
        return practice;
    }

    @Transactional
    public Practice setActive(WorkspaceContext ctx, String slug, boolean active) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        if (practice.isActive() == active) {
            return practice;
        }

        practice.setActive(active);
        practice = practiceRepository.save(practice);
        log.info("Set practice '{}' (slug={}) active={} in workspace {}", practice.getName(), slug, active, ctx.slug());
        return practice;
    }

    @Transactional
    public void deletePractice(WorkspaceContext ctx, String slug) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        practiceRepository.delete(practice);
        log.info("Deleted practice '{}' (slug={}) from workspace {}", practice.getName(), slug, ctx.slug());
    }
}
