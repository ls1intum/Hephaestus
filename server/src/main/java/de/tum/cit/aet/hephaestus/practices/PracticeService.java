package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.ClearablePracticeField;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
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
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    /**
     * Refuses a tier an administrator may not select yet.
     *
     * <p>{@code PROPOSE} is in the vocabulary and in the DB CHECK so the ladder reads whole and so the value
     * is already representable, but the approval queue that would let a human act on a prepared unit does
     * not exist. A practice parked there would prepare feedback nobody can approve and swallow it, which is
     * strictly worse than the tier not being offered — so the refusal names the missing thing rather than
     * calling the value invalid.
     */
    private static void requireSelectable(@Nullable PracticeReviewTier tier) {
        if (tier != null && !tier.selectable()) {
            throw new IllegalArgumentException(
                "Propose is not available yet: feedback would be prepared with no way for anyone to approve " +
                    "it. Use Observe to keep measuring in silence, or Deliver to send feedback without approval."
            );
        }
    }

    /**
     * The workspace catalogue, optionally narrowed to one tier.
     *
     * <p>The filter is on the <em>effective</em> tier, which is the only tier an administrator can see on
     * the screen they are filtering. Filtering the stored column instead would answer "which practices
     * happen to hold this value", and would return nothing at all for the tier most practices are actually
     * at — the inherited one.
     */
    @Transactional(readOnly = true)
    public List<Practice> listPractices(WorkspaceContext ctx, @Nullable PracticeReviewTier reviewTier) {
        log.debug("Listing practices for workspace {} (reviewTier={})", ctx.slug(), reviewTier);
        List<Practice> all = practiceRepository.findAllForCatalog(ctx.id());
        if (reviewTier == null) {
            return all;
        }
        PracticeReviewTier workspaceDefault = workspaceDefaults.forWorkspace(ctx.id()).defaultTier();
        return all
            .stream()
            .filter(p -> ReviewTierResolver.effectiveTierOf(p, workspaceDefault) == reviewTier)
            .toList();
    }

    /**
     * Every practice this workspace actually reviews, at any effective tier above {@code OFF}.
     *
     * <p>Includes {@code OBSERVE}: that tier promises the developer no <em>feedback</em>, not concealment,
     * so the learner-facing catalogue lists what is observed while the tier governs what is said.
     */
    @Transactional(readOnly = true)
    public List<Practice> listReviewedPractices(WorkspaceContext ctx) {
        PracticeReviewTier workspaceDefault = workspaceDefaults.forWorkspace(ctx.id()).defaultTier();
        return practiceRepository
            .findAllForCatalog(ctx.id())
            .stream()
            .filter(p -> ReviewTierResolver.effectiveTierOf(p, workspaceDefault).admitsReview())
            .toList();
    }

    @Transactional
    public void reorderPractices(WorkspaceContext ctx, String areaSlug, List<String> orderedSlugs) {
        if (new HashSet<>(orderedSlugs).size() != orderedSlugs.size()) {
            throw new IllegalArgumentException("orderedSlugs must not contain duplicate slugs");
        }
        lockWorkspace(ctx);
        List<Practice> bucket = practiceRepository
            .findAllForCatalog(ctx.id())
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
        List<Practice> allPractices = practiceRepository.findAllForCatalog(ctx.id());
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
        // A new practice holds NO opinion of its own and inherits its area's, and through it the
        // workspace's. It used to be stamped with the default tier at creation, which is how forty
        // practices came to carry an opinion nobody had expressed and why turning the system down meant
        // editing every one of them. The exception is a practice whose policy cannot attempt an automated
        // review: that is not a preference to be inherited over, it is a fact about the practice, so it is
        // written explicitly.
        practice.setReviewTier(
            definition.automatedReviewPolicy().automatedReview().canAttemptAutomatedReview()
                ? null
                : PracticeReviewTier.OFF
        );
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
        List<PracticeBinding> bindings = request.bindings() == null ? beforeDefinition.bindings() : request.bindings();
        // The kind is read off the bindings, so "the author moved this practice to another kind of
        // work" is a question about the new bindings rather than a separate field to compare.
        ArtifactKind artifactKind = PracticeBinding.artifactKindOf(bindings);
        PracticeAutomatedReviewPolicy automatedReviewPolicy =
            request.automatedReviewPolicy() != null
                ? request.automatedReviewPolicy()
                : artifactKind.equals(beforeDefinition.artifactKind())
                    ? beforeDefinition.automatedReviewPolicy()
                    : evidenceDefaults.policyFor(artifactKind);
        boolean removesAutomatedReview =
            request.automatedReviewPolicy() != null &&
            !automatedReviewPolicy.automatedReview().canAttemptAutomatedReview();
        if (removesAutomatedReview) {
            // A practice nobody automates still says what occasions it — that is where its kind comes
            // from — but it reads nothing, so the evidence goes with the automation that read it.
            bindings = bindings.stream().map(PracticeService::withoutEvidence).toList();
        }
        PracticeDefinition afterDefinition = new PracticeDefinition(
            request.name() == null ? beforeDefinition.name() : request.name(),
            bindings,
            request.criteria() == null ? beforeDefinition.criteria() : request.criteria(),
            removesAutomatedReview && request.precomputeScript() == null
                ? null
                : patch(
                      beforeDefinition.precomputeScript(),
                      request.precomputeScript(),
                      fieldsToClear.contains(ClearablePracticeField.PRECOMPUTE_SCRIPT)
                  ),
            automatedReviewPolicy,
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
        PracticeReviewTier tierBefore = practice.getReviewTier();
        applyDefinition(practice, afterDefinition);
        if (!afterDefinition.automatedReviewPolicy().automatedReview().canAttemptAutomatedReview()) {
            practice.setReviewTier(PracticeReviewTier.OFF);
        }
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
        if (tierBefore != practice.getReviewTier()) {
            configAudit.record(
                ConfigAuditEntry.updated(
                    ConfigAuditEntityType.PRACTICE_USAGE,
                    practice.getId(),
                    ctx.id(),
                    new PracticeUsageSnapshot(tierBefore),
                    new PracticeUsageSnapshot(practice.getReviewTier())
                )
            );
        }
        log.info("Updated practice '{}' (slug={}) in workspace {}", practice.getName(), slug, ctx.slug());
        return practice;
    }

    /**
     * Sets one practice's own tier, or clears it back to inherit.
     *
     * @param reviewTier the tier to hold, or {@code null} to hold none and inherit the area's — and through
     *     it the workspace's. Clearing has to be expressible or the chain is write-once: an administrator
     *     who set one practice explicitly could never put it back under the area's decision.
     */
    @Transactional
    public Practice setReviewTier(WorkspaceContext ctx, String slug, @Nullable PracticeReviewTier reviewTier) {
        lockWorkspace(ctx);
        Practice practice = practiceRepository
            .findByWorkspaceIdAndSlug(ctx.id(), slug)
            .orElseThrow(() -> new EntityNotFoundException("Practice", slug));

        PracticeReviewTier before = practice.getReviewTier();
        if (before == reviewTier) {
            return practice;
        }
        requireSelectable(reviewTier);
        // Every tier above OFF starts a review, so every tier above OFF needs a policy that can run one.
        // Asked of the tier that would be IN FORCE, not of the one being written: "inherit" is a request
        // for whatever the area says, and if that admits a review the practice still cannot run it.
        PracticeReviewTier effective = ReviewTierResolver.resolvePractice(
            reviewTier,
            practice.getArea() == null ? null : practice.getArea().getReviewTier(),
            workspaceDefaults.forWorkspace(ctx.id()).defaultTier()
        ).tier();
        if (
            effective.admitsReview() &&
            !practice.getAutomatedReviewPolicy().automatedReview().canAttemptAutomatedReview()
        ) {
            throw new IllegalArgumentException(
                "This practice cannot be used in automated reviews with its current review settings"
            );
        }

        practice.setReviewTier(reviewTier);
        practice = practiceRepository.save(practice);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_USAGE,
                practice.getId(),
                ctx.id(),
                new PracticeUsageSnapshot(before),
                new PracticeUsageSnapshot(reviewTier)
            )
        );
        log.info(
            "Set practice '{}' (slug={}) reviewTier={} in workspace {}",
            practice.getName(),
            slug,
            reviewTier,
            ctx.slug()
        );
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
        ArtifactKind artifactKind = PracticeBinding.artifactKindOf(request.bindings());
        return new PracticeDefinition(
            request.name(),
            request.bindings(),
            request.criteria(),
            request.precomputeScript(),
            request.automatedReviewPolicy() == null
                ? evidenceDefaults.policyFor(artifactKind)
                : request.automatedReviewPolicy(),
            request.whyItMatters(),
            request.whatGoodLooksLike(),
            request.areaSlug()
        );
    }

    private static PracticeBinding withoutEvidence(PracticeBinding binding) {
        return new PracticeBinding(binding.signals(), List.of(), binding.onDrafts());
    }

    private static void applyDefinition(Practice practice, PracticeDefinition definition) {
        practice.setName(definition.name());
        practice.setBindings(definition.bindings());
        practice.setCriteria(definition.criteria());
        practice.setPrecomputeScript(definition.precomputeScript());
        practice.setAutomatedReviewPolicy(definition.automatedReviewPolicy());
        practice.setWhyItMatters(definition.whyItMatters());
        practice.setWhatGoodLooksLike(definition.whatGoodLooksLike());
    }

    private static String patch(String current, String replacement, boolean clear) {
        return replacement != null ? replacement : clear ? null : current;
    }
}
