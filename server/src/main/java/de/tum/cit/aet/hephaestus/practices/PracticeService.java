package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.dto.ClearablePracticeField;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final PracticeAreaService practiceAreaService;
    private final ConfigAuditPort configAudit;
    private final PracticeRevisionRepository practiceRevisionRepository;
    private final WorkspaceRepository workspaceRepository;

    private static final Pattern UPPERCASE_DETECTOR_VOCAB = Pattern.compile(
        "\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b"
    );

    @Transactional(readOnly = true)
    public List<Practice> listPractices(WorkspaceContext ctx, Boolean active) {
        log.debug("Listing practices for workspace {} (active={})", ctx.slug(), active);
        return practiceRepository.findByFilters(ctx.id(), active);
    }

    @Transactional
    public void reorderPractices(WorkspaceContext ctx, String areaSlug, List<String> orderedSlugs) {
        if (new HashSet<>(orderedSlugs).size() != orderedSlugs.size()) {
            throw new IllegalArgumentException("orderedSlugs must not contain duplicate slugs");
        }
        lockWorkspace(ctx);
        List<Practice> bucket = practiceRepository
            .findByFilters(ctx.id(), null)
            .stream()
            .filter(p -> Objects.equals(areaSlug, p.getArea() == null ? null : p.getArea().getSlug()))
            .toList();
        Set<String> existing = bucket.stream().map(Practice::getSlug).collect(Collectors.toSet());
        Set<String> requested = new HashSet<>(orderedSlugs);
        if (!existing.equals(requested)) {
            String unknown = requested
                .stream()
                .filter(s -> !existing.contains(s))
                .findFirst()
                .orElse(null);
            if (unknown != null) {
                throw new EntityNotFoundException("Practice", unknown);
            }
            throw new IllegalArgumentException(
                "orderedSlugs must contain every practice in the area (a complete ordering)"
            );
        }
        Map<String, Practice> bySlug = bucket.stream().collect(Collectors.toMap(Practice::getSlug, p -> p));
        int order = 0;
        for (String slug : orderedSlugs) {
            Practice p = bySlug.get(slug);
            p.setDisplayOrder(order++);
            practiceRepository.save(p);
        }
    }

    @Transactional
    public List<Practice> placePractice(WorkspaceContext ctx, String practiceSlug, String areaSlug, int position) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), practiceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", practiceSlug));
        PracticeArea destination =
            areaSlug == null
                ? null
                : practiceAreaRepository
                      .findByWorkspaceIdAndSlug(ctx.id(), areaSlug)
                      .orElseThrow(() -> new EntityNotFoundException("PracticeArea", areaSlug));

        Long sourceAreaId = practice.getArea() == null ? null : practice.getArea().getId();
        Long destinationAreaId = destination == null ? null : destination.getId();
        List<Practice> allPractices = practiceRepository.findByFilters(ctx.id(), null);
        List<Practice> source = practicesInArea(allPractices, sourceAreaId, practice);
        List<Practice> target = Objects.equals(sourceAreaId, destinationAreaId)
            ? source
            : practicesInArea(allPractices, destinationAreaId, practice);

        if (position > target.size()) {
            throw new IllegalArgumentException("position exceeds the destination size");
        }
        target.add(position, practice);
        practice.setArea(destination);
        resequence(target);
        if (!Objects.equals(sourceAreaId, destinationAreaId)) {
            resequence(source);
        }
        return allPractices;
    }

    private List<Practice> practicesInArea(List<Practice> allPractices, Long areaId, Practice excluded) {
        return allPractices
            .stream()
            .filter(practice -> Objects.equals(areaId, practice.getArea() == null ? null : practice.getArea().getId()))
            .filter(practice -> !practice.getId().equals(excluded.getId()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void resequence(List<Practice> practices) {
        for (int index = 0; index < practices.size(); index++) {
            practices.get(index).setDisplayOrder(index);
        }
        practiceRepository.saveAll(practices);
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

        Workspace workspace = lockWorkspace(ctx);

        Practice practice = new Practice();
        var area =
            request.areaSlug() == null
                ? null
                : practiceAreaRepository
                      .findByWorkspaceIdAndSlug(ctx.id(), request.areaSlug())
                      .orElseThrow(() -> new EntityNotFoundException("PracticeArea", request.areaSlug()));
        practice.setWorkspace(workspace);
        practice.setArea(area);
        practice.setDisplayOrder(
            practiceRepository.findMaxDisplayOrder(ctx.id(), area == null ? null : area.getId()) + 1
        );
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
            if (!DataIntegrityViolationConstraints.hasName(ex, "uk_practice_workspace_slug")) {
                throw ex;
            }
            throw new PracticeSlugConflictException(
                "A practice with slug '" + request.slug() + "' already exists in this workspace.",
                ex
            );
        }
        int revisionNumber = snapshotRevision(practice);
        configAudit.record(
            ConfigAuditEntry.created(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                ctx.id(),
                PracticeDefinitionSnapshot.of(practice, revisionNumber)
            )
        );

        log.info("Created practice '{}' (slug={}) in workspace {}", practice.getName(), practice.getSlug(), ctx.slug());
        return practice;
    }

    private Workspace lockWorkspace(WorkspaceContext ctx) {
        return workspaceRepository
            .findByIdForUpdate(ctx.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));
    }

    private int snapshotRevision(Practice practice) {
        practiceRepository.findByIdForUpdate(practice.getId());
        int revisionNumber = nextRevisionNumber(practice);
        practiceRevisionRepository.save(new PracticeRevision(practice, revisionNumber, practice.getCriteria()));
        return revisionNumber;
    }

    private int nextRevisionNumber(Practice practice) {
        return practiceRevisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(r -> r.getRevisionNumber() + 1)
            .orElse(1);
    }

    private static void validateLearnerContent(Practice practice) {
        rejectDetectorVocab("whatGoodLooksLike", practice.getWhatGoodLooksLike());
        rejectDetectorVocab("whyItMatters", practice.getWhyItMatters());
    }

    private static void rejectDetectorVocab(String field, String value) {
        if (value != null && UPPERCASE_DETECTOR_VOCAB.matcher(value).find()) {
            throw new IllegalArgumentException(
                field +
                    " is learner-facing and must not contain detector presence/assessment vocabulary" +
                    " (PRESENT / ABSENT / GOOD / BAD / NOT_APPLICABLE)."
            );
        }
    }

    private void validateTriggerEventsForFocus(Practice practice) {
        var allowed = TriggerEventCatalog.eligibleFor(practice.getArtifactType());
        List<String> triggerEvents = TriggerEventsConverter.toList(practice.getTriggerEvents());
        if (practice.getArtifactType() != WorkArtifact.CONVERSATION_THREAD && triggerEvents.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one trigger event is required for " + practice.getArtifactType()
            );
        }
        List<String> incompatible = triggerEvents
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
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));
        Integer revisionNumber = currentRevisionNumber(practice);
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, revisionNumber);

        boolean changed = false;
        boolean areaChanged = false;
        boolean criteriaChanged = false;
        Set<ClearablePracticeField> fieldsToClear = request.clear() == null ? Set.of() : request.clear();
        if (
            fieldsToClear.contains(ClearablePracticeField.PRECOMPUTE_SCRIPT) && practice.getPrecomputeScript() != null
        ) {
            practice.setPrecomputeScript(null);
            changed = true;
        }
        if (fieldsToClear.contains(ClearablePracticeField.WHY_IT_MATTERS) && practice.getWhyItMatters() != null) {
            practice.setWhyItMatters(null);
            changed = true;
        }
        if (
            fieldsToClear.contains(ClearablePracticeField.WHAT_GOOD_LOOKS_LIKE) &&
            practice.getWhatGoodLooksLike() != null
        ) {
            practice.setWhatGoodLooksLike(null);
            changed = true;
        }
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
        if (request.area() != null) {
            areaChanged = practiceAreaService.applyBinding(ctx, practice, request.area().areaSlug());
        }

        if (!changed && !areaChanged) {
            return practice;
        }

        if (changed) {
            validateTriggerEventsForFocus(practice);
            validateLearnerContent(practice);
        }
        practice = practiceRepository.save(practice);
        if (changed) {
            if (criteriaChanged) {
                revisionNumber = snapshotRevision(practice);
            }
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
        log.info("Updated practice '{}' (slug={}) in workspace {}", practice.getName(), slug, ctx.slug());
        return practice;
    }

    @Transactional
    public Practice setActive(WorkspaceContext ctx, String slug, boolean active) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        if (practice.isActive() == active) {
            return practice;
        }

        practice.setActive(active);
        practice = practiceRepository.save(practice);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_ACTIVE,
                practice.getId(),
                ctx.id(),
                new PracticeActiveSnapshot(!active),
                new PracticeActiveSnapshot(active)
            )
        );
        log.info("Set practice '{}' (slug={}) active={} in workspace {}", practice.getName(), slug, active, ctx.slug());
        return practice;
    }

    @Transactional
    public void deletePractice(WorkspaceContext ctx, String slug) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        Long practiceId = practice.getId();
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, currentRevisionNumber(practice));
        practiceRepository.delete(practice);
        configAudit.record(
            ConfigAuditEntry.deleted(ConfigAuditEntityType.PRACTICE_DEFINITION, practiceId, ctx.id(), before)
        );
        log.info("Deleted practice '{}' (slug={}) from workspace {}", practice.getName(), slug, ctx.slug());
    }

    private Integer currentRevisionNumber(Practice practice) {
        return practiceRevisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(PracticeRevision::getRevisionNumber)
            .orElse(null);
    }
}
