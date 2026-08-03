package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.dto.ClearablePracticeField;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
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
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final PracticeAreaService practiceAreaService;
    private final ConfigAuditPort configAudit;
    private final PracticeRevisionService practiceRevisionService;
    private final WorkspaceRepository workspaceRepository;
    private final PracticeDefinitionValidator definitionValidator;
    private final PracticeEvidenceDefaults evidenceDefaults;

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
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, currentRevisionNumber(practice));
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
            int revisionNumber = practiceRevisionService.append(practice).getRevisionNumber();
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
        return createPractice(ctx, request.slug(), definition(request), null, null);
    }

    @Transactional
    public Practice createPracticeFromCatalog(WorkspaceContext ctx, String slug, PracticeDefinition definition) {
        return createPractice(ctx, slug, definition, slug, definition.provenanceFingerprint(slug));
    }

    private Practice createPractice(
        WorkspaceContext ctx,
        String slug,
        PracticeDefinition definition,
        @Nullable String sourceCuratedSlug,
        @Nullable String sourceCuratedFingerprint
    ) {
        if (practiceRepository.existsByWorkspaceIdAndSlug(ctx.id(), slug)) {
            throw new PracticeSlugConflictException(
                "A practice with slug '" + slug + "' already exists in this workspace."
            );
        }

        Workspace workspace = lockWorkspace(ctx);

        Practice practice = new Practice();
        var area =
            definition.areaSlug() == null
                ? null
                : practiceAreaRepository
                      .findByWorkspaceIdAndSlug(ctx.id(), definition.areaSlug())
                      .orElseThrow(() -> new EntityNotFoundException("PracticeArea", definition.areaSlug()));
        practice.setWorkspace(workspace);
        practice.setSourceCuratedSlug(sourceCuratedSlug);
        practice.setSourceCuratedFingerprint(sourceCuratedFingerprint);
        practice.setArea(area);
        practice.setDisplayOrder(
            practiceRepository.findMaxDisplayOrder(ctx.id(), area == null ? null : area.getId()) + 1
        );
        practice.setSlug(slug);
        applyDefinition(practice, definition);
        definitionValidator.validate(definition);

        try {
            practice = practiceRepository.save(practice);
        } catch (DataIntegrityViolationException ex) {
            if (!DataIntegrityViolationConstraints.hasName(ex, "uk_practice_workspace_slug")) {
                throw ex;
            }
            throw new PracticeSlugConflictException(
                "A practice with slug '" + slug + "' already exists in this workspace.",
                ex
            );
        }
        int revisionNumber = practiceRevisionService.append(practice).getRevisionNumber();
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

    @Transactional
    public Practice updatePractice(WorkspaceContext ctx, String slug, UpdatePracticeRequestDTO request) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));
        Integer revisionNumber = currentRevisionNumber(practice);
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, revisionNumber);
        PracticeDefinition beforeDefinition = PracticeDefinition.from(practice);

        Set<ClearablePracticeField> fieldsToClear = request.clear() == null ? Set.of() : request.clear();
        WorkArtifact artifactType =
            request.artifactType() == null ? beforeDefinition.artifactType() : request.artifactType();
        PracticeDefinition afterDefinition = new PracticeDefinition(
            request.name() == null ? beforeDefinition.name() : request.name(),
            artifactType,
            request.triggerEvents() == null ? beforeDefinition.triggerEvents() : request.triggerEvents(),
            request.criteria() == null ? beforeDefinition.criteria() : request.criteria(),
            patch(
                beforeDefinition.precomputeScript(),
                request.precomputeScript(),
                fieldsToClear.contains(ClearablePracticeField.PRECOMPUTE_SCRIPT)
            ),
            request.evidence() != null
                ? request.evidence()
                : artifactType == beforeDefinition.artifactType()
                    ? beforeDefinition.evidence()
                    : evidenceDefaults.forArtifact(artifactType),
            patch(
                beforeDefinition.whyItMatters(),
                request.whyItMatters(),
                fieldsToClear.contains(ClearablePracticeField.WHY_IT_MATTERS)
            ),
            patch(
                beforeDefinition.whatGoodLooksLike(),
                request.whatGoodLooksLike(),
                fieldsToClear.contains(ClearablePracticeField.WHAT_GOOD_LOOKS_LIKE)
            ),
            request.area() == null ? beforeDefinition.areaSlug() : request.area().areaSlug()
        );

        if (afterDefinition.equals(beforeDefinition)) {
            return practice;
        }

        if (request.area() != null) {
            practiceAreaService.applyBinding(ctx, practice, request.area().areaSlug());
        }
        applyDefinition(practice, afterDefinition);
        definitionValidator.validate(afterDefinition);
        practice = practiceRepository.save(practice);
        revisionNumber = practiceRevisionService.append(practice).getRevisionNumber();
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                ctx.id(),
                before,
                PracticeDefinitionSnapshot.of(practice, revisionNumber)
            )
        );
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
        return practiceRevisionService.currentRevisionNumber(practice);
    }

    private PracticeDefinition definition(CreatePracticeRequestDTO request) {
        WorkArtifact artifactType = request.artifactType() == null ? WorkArtifact.PULL_REQUEST : request.artifactType();
        return new PracticeDefinition(
            request.name(),
            artifactType,
            request.triggerEvents(),
            request.criteria(),
            request.precomputeScript(),
            request.evidence() == null ? evidenceDefaults.forArtifact(artifactType) : request.evidence(),
            request.whyItMatters(),
            request.whatGoodLooksLike(),
            request.areaSlug()
        );
    }

    private static void applyDefinition(Practice practice, PracticeDefinition definition) {
        practice.setName(definition.name());
        practice.setArtifactType(definition.artifactType());
        practice.setTriggerEvents(definition.triggerEventsJson());
        practice.setCriteria(definition.criteria());
        practice.setPrecomputeScript(definition.precomputeScript());
        practice.setEvidence(definition.evidence());
        practice.setWhyItMatters(definition.whyItMatters());
        practice.setWhatGoodLooksLike(definition.whatGoodLooksLike());
    }

    private static String patch(String current, String replacement, boolean clear) {
        return replacement != null ? replacement : clear ? null : current;
    }
}
