package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntry;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeReleaseService;
import de.tum.cit.aet.hephaestus.practices.curated.EffectiveCatalog;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseProposalDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.AutomatedReviewFence;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeReleaseService {

    private static final Logger log = LoggerFactory.getLogger(PracticeReleaseService.class);

    private final PracticeRepository practices;
    private final WorkspaceRepository workspaces;
    private final CuratedCatalogService catalogService;
    private final CuratedPracticeReleaseService instanceReleases;
    private final PracticeGroupService groups;
    private final PracticeDefinitionValidator validator;
    private final PracticeRevisionService revisions;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;
    private final AutomatedReviewFence fence;
    private final ConfigAuditPort audit;

    @Transactional(readOnly = true)
    public List<PracticeReleaseProposalDTO> list(WorkspaceContext ctx) {
        EffectiveCatalog catalog = catalogService.catalog();
        return practices.findAllForCatalog(ctx.id()).stream()
                .map(practice -> proposal(practice, catalog))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public PracticeReleaseProposalDTO get(WorkspaceContext ctx, String slug) {
        Practice practice = practice(ctx, slug);
        return requireProposal(practice, catalogService.catalog());
    }

    @Transactional
    public Practice accept(
            WorkspaceContext ctx,
            String slug,
            @Nullable EntityTagPrecondition precondition,
            Map<PracticeDefinitionField, PracticeReleaseChoice> choices) {
        // The catalogue before the workspace, as catalogue adoption and repair take them.
        EffectiveCatalog catalog = instanceReleases.catalogForDecision();
        Workspace workspace = lockWorkspace(ctx);
        Practice practice = practice(ctx, slug);
        PracticeReleaseProposalDTO proposal = requireProposal(practice, catalog);
        PracticeReleasePrecondition.requireCurrent(precondition, proposal);
        PracticeDefinition merged =
                PracticeDefinitionMerge.apply(proposal.base(), proposal.current(), proposal.offered(), choices);
        validator.validate(merged);

        Integer oldRevision = revisions.currentRevisionNumber(practice);
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, oldRevision);
        PracticeAutonomy autonomyBefore = practice.getAutonomy();
        PracticeAutonomy effectiveBefore = effectiveAutonomy(practice, ctx.id());
        if (!Objects.equals(proposal.current().groupSlug(), merged.groupSlug())) {
            groups.applyBinding(ctx, practice, merged.groupSlug());
        }
        PracticeService.applyDefinition(practice, merged);
        if (!fence.effectivePolicy(practice).automatedReview().canAttemptAutomatedReview()) {
            practice.setAutonomy(PracticeAutonomy.OFF);
        }
        practice.setAdoptedBase(proposal.offered());
        practice.setAdoptedBaseSource(AdoptedBaseSource.EXACT_ADOPTION);
        practice.setSourceCuratedFingerprint(proposal.offered().provenanceFingerprint(slug));
        practice.setDeclinedOfferedDigest(null);
        practices.save(practice);
        int revision = revisions.append(practice).getRevisionNumber();
        audit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                ctx.id(),
                before,
                PracticeDefinitionSnapshot.of(practice, revision)));
        if (autonomyBefore != practice.getAutonomy()) {
            audit.record(ConfigAuditEntry.updated(
                    ConfigAuditEntityType.PRACTICE_USAGE,
                    practice.getId(),
                    ctx.id(),
                    new PracticeUsageSnapshot(autonomyBefore),
                    new PracticeUsageSnapshot(practice.getAutonomy())));
        }
        if (effectiveBefore != effectiveAutonomy(practice, ctx.id())) {
            workspace.getReviewSettings().incrementRolloutRevision();
        }
        return practice;
    }

    /**
     * Moves an unchanged copy of a bundled practice script to the program the catalog now offers: when the practice's
     * script is the one its adopted base holds and the offer holds another. Only the script changes, on the practice
     * and in its adopted base; every other field, its pending offer, the adopted base source, a declined offer and
     * the provenance fingerprint stay. A diverged copy is left as it is, with its normal offer. Appends a revision and
     * records a system audit entry.
     *
     * @return whether the copy took the offered script
     */
    @Transactional
    public boolean adoptOfferedPracticeScript(Long workspaceId, String slug) {
        EffectiveCatalog catalog = instanceReleases.catalogForDecision();
        Practice practice =
                practices.findByWorkspaceIdAndSlug(workspaceId, slug).orElse(null);
        PracticeReleaseProposalDTO proposal = practice == null ? null : proposal(practice, catalog);
        if (practice == null
                || proposal == null
                || !Objects.equals(
                        proposal.base().practiceScript(), proposal.current().practiceScript())
                || Objects.equals(
                        proposal.offered().practiceScript(), proposal.current().practiceScript())) {
            return false;
        }
        String script = proposal.offered().practiceScript();
        try {
            validator.validate(proposal.current().withPracticeScript(script));
        } catch (IllegalArgumentException invalid) {
            // Thrown past this method, it would roll back every other copy of the workspace on each start.
            log.warn(
                    "Kept a practice script the shipped program cannot replace: workspaceId={} slug={} reason={}",
                    workspaceId,
                    slug,
                    invalid.getMessage());
            return false;
        }
        Integer oldRevision = revisions.currentRevisionNumber(practice);
        PracticeDefinitionSnapshot before = PracticeDefinitionSnapshot.of(practice, oldRevision);
        practice.setPracticeScript(script);
        practice.setAdoptedBase(proposal.base().withPracticeScript(script));
        practices.save(practice);
        int revision = revisions.append(practice).getRevisionNumber();
        audit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                workspaceId,
                before,
                PracticeDefinitionSnapshot.of(practice, revision)));
        return true;
    }

    /** The slugs of the workspace's practices that have a release proposal. */
    @Transactional(readOnly = true)
    public List<String> proposedSlugs(Long workspaceId) {
        EffectiveCatalog catalog = catalogService.catalog();
        return practices.findAllForCatalog(workspaceId).stream()
                .filter(practice -> proposal(practice, catalog) != null)
                .map(Practice::getSlug)
                .toList();
    }

    @Transactional
    public void decline(WorkspaceContext ctx, String slug, @Nullable EntityTagPrecondition precondition) {
        EffectiveCatalog catalog = instanceReleases.catalogForDecision();
        lockWorkspace(ctx);
        Practice practice = practice(ctx, slug);
        PracticeReleaseProposalDTO proposal = requireProposal(practice, catalog);
        PracticeReleasePrecondition.requireCurrent(precondition, proposal);
        PracticeDefinitionSnapshot before =
                PracticeDefinitionSnapshot.of(practice, revisions.currentRevisionNumber(practice));
        practice.setDeclinedOfferedDigest(proposal.offeredDigest());
        practices.save(practice);
        audit.record(ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_DEFINITION,
                practice.getId(),
                ctx.id(),
                before,
                PracticeDefinitionSnapshot.of(practice, revisions.currentRevisionNumber(practice))));
    }

    private Practice practice(WorkspaceContext ctx, String slug) {
        return practices
                .findByWorkspaceIdAndSlug(ctx.id(), slug)
                .orElseThrow(() -> new EntityNotFoundException("Practice", slug));
    }

    private Workspace lockWorkspace(WorkspaceContext ctx) {
        return workspaces
                .findByIdForUpdate(ctx.id())
                .orElseThrow(() -> new EntityNotFoundException("Workspace", ctx.slug()));
    }

    private PracticeAutonomy effectiveAutonomy(Practice practice, Long workspaceId) {
        return AutonomyResolver.effectiveAutonomyOf(
                practice, workspaceDefaults.forWorkspace(workspaceId).defaultAutonomy());
    }

    private PracticeReleaseProposalDTO requireProposal(Practice practice, EffectiveCatalog catalog) {
        PracticeReleaseProposalDTO proposal = proposal(practice, catalog);
        if (proposal == null) {
            throw new EntityNotFoundException("Practice release", practice.getSlug());
        }
        return proposal;
    }

    private static @Nullable PracticeReleaseProposalDTO proposal(Practice practice, EffectiveCatalog catalog) {
        String sourceSlug = practice.getSourceCuratedSlug();
        PracticeDefinition base = practice.getAdoptedBase();
        if (sourceSlug == null || base == null) {
            return null;
        }
        CatalogEntry<PracticeDefinition> offer = catalog.practice(sourceSlug).orElse(null);
        if (offer == null || base.equals(offer.effective())) {
            return null;
        }
        String offeredDigest = offer.effective().exactFingerprint(sourceSlug);
        if (offeredDigest.equals(practice.getDeclinedOfferedDigest())) {
            return null;
        }
        return PracticeReleaseProposalDTO.of(
                sourceSlug,
                base,
                PracticeDefinition.from(practice),
                offer.effective(),
                Objects.requireNonNull(practice.getAdoptedBaseSource()),
                practice.getCurrentRevision() == null
                        ? null
                        : practice.getCurrentRevision().getRevisionNumber());
    }
}
