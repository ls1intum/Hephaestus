package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.regex.Pattern;
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
    private final PracticeRevisionRepository practiceRevisionRepository;
    private final WorkspaceRepository workspaceRepository;

    /**
     * The detector's current presence/assessment vocabulary that must never leak into learner-facing copy (the
     * authoring guard). Matched case-sensitively as standalone ALL-CAPS tokens so ordinary prose ("good", "bad",
     * "absent") is not rejected — only the literal enum tokens the detector emits. Mirrors how the parser
     * tokenizes (uppercase enum names: {@link de.tum.cit.aet.hephaestus.practices.model.Presence} /
     * {@link de.tum.cit.aet.hephaestus.practices.model.Assessment}).
     */
    private static final Pattern DETECTOR_VOCAB = Pattern.compile(
        "\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b"
    );

    @Transactional(readOnly = true)
    public List<Practice> listPractices(WorkspaceContext ctx, Boolean active) {
        log.debug("Listing practices for workspace {} (active={})", ctx.slug(), active);
        return practiceRepository.findByFilters(ctx.id(), active);
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
        practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(request.triggerEvents()));
        practice.setCriteria(request.criteria());
        practice.setPrecomputeScript(request.precomputeScript());
        practice.setWhyItMatters(request.whyItMatters());
        practice.setWhatGoodLooksLike(request.whatGoodLooksLike());
        if (request.artifactType() != null) {
            practice.setArtifactType(request.artifactType());
        }
        validateTriggerEventsForFocus(practice);
        validateLearnerContent(practice);

        try {
            practice = practiceRepository.save(practice);
        } catch (DataIntegrityViolationException ex) {
            // Safety net for race condition: concurrent create with same slug
            throw new PracticeSlugConflictException(
                "A practice with slug '" + request.slug() + "' already exists in this workspace.",
                ex
            );
        }
        snapshotRevision(practice); // revision 1 — pins the criteria as authored (reproducibility, SCD-2)

        log.info("Created practice '{}' (slug={}) in workspace {}", practice.getName(), practice.getSlug(), ctx.slug());
        return practice;
    }

    /**
     * Append a new {@link PracticeRevision} snapshotting the practice's current {@code criteria}. Called on
     * create (revision 1) and whenever {@code criteria} changes, so every finding can pin to the criteria
     * version it was detected against (the criteria as it was).
     */
    private void snapshotRevision(Practice practice) {
        int next = practiceRevisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(r -> r.getRevisionNumber() + 1)
            .orElse(1);
        practiceRevisionRepository.save(new PracticeRevision(practice, next, practice.getCriteria()));
    }

    /**
     * Authoring guard for the learner-facing layer: {@code whatGoodLooksLike} is a concrete exemplar, so it
     * must not leak the detector's observation vocabulary — this keeps the learner view free of the rubric.
     */
    private static void validateLearnerContent(Practice practice) {
        String exemplar = practice.getWhatGoodLooksLike();
        if (exemplar != null && DETECTOR_VOCAB.matcher(exemplar).find()) {
            throw new IllegalArgumentException(
                "whatGoodLooksLike is learner-facing and must be a concrete exemplar — it must not contain" +
                    " detector presence/assessment vocabulary (PRESENT / ABSENT / GOOD / BAD / NOT_APPLICABLE)."
            );
        }
    }

    /**
     * Reject trigger events incompatible with the practice's focus — a PR practice listening for an
     * issue event (or vice versa) can never fire, so it is a configuration error rather than dead config.
     */
    private void validateTriggerEventsForFocus(Practice practice) {
        var allowed = TriggerEventCatalog.eligibleFor(practice.getArtifactType());
        List<String> incompatible = TriggerEventsConverter.toList(practice.getTriggerEvents())
            .stream()
            .filter(event -> !allowed.contains(event))
            .toList();
        if (!incompatible.isEmpty()) {
            throw new IllegalArgumentException(
                "Trigger events " +
                    incompatible +
                    " are not valid for a " +
                    practice.getArtifactType() +
                    " practice. Allowed events for this focus: " +
                    allowed
            );
        }
    }

    @Transactional
    public Practice updatePractice(WorkspaceContext ctx, String slug, UpdatePracticeRequestDTO request) {
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        boolean changed = false;
        boolean criteriaChanged = false;
        if (request.name() != null) {
            practice.setName(request.name());
            changed = true;
        }
        if (request.triggerEvents() != null) {
            practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(request.triggerEvents()));
            changed = true;
        }
        if (request.criteria() != null && !request.criteria().equals(practice.getCriteria())) {
            practice.setCriteria(request.criteria());
            changed = true;
            criteriaChanged = true;
        }
        if (request.precomputeScript() != null) {
            practice.setPrecomputeScript(request.precomputeScript());
            changed = true;
        }
        if (request.whyItMatters() != null) {
            practice.setWhyItMatters(request.whyItMatters());
            changed = true;
        }
        if (request.whatGoodLooksLike() != null) {
            practice.setWhatGoodLooksLike(request.whatGoodLooksLike());
            changed = true;
        }
        if (request.artifactType() != null) {
            practice.setArtifactType(request.artifactType());
            changed = true;
        }

        if (!changed) {
            return practice;
        }

        validateTriggerEventsForFocus(practice);
        validateLearnerContent(practice);
        practice = practiceRepository.save(practice);
        if (criteriaChanged) {
            snapshotRevision(practice); // the criteria changed — append a new revision so it is recorded, not overwritten
        }
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
