package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.AdoptedBaseSource;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionMerge;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.PracticeReleasePrecondition;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseProposalDTO;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("The instance catalog is global")
public class CuratedPracticeReleaseService {

    private final CuratedCatalogService catalog;
    private final CuratedCatalogLock lock;
    private final CuratedPracticeOverrideRepository overrides;
    private final PracticeDefinitionValidator validator;
    private final Clock clock;

    /** Keeps an offered instance definition stable until a workspace decision commits. */
    @Transactional
    public EffectiveCatalog catalogForDecision() {
        lock.acquire();
        return catalog.catalog();
    }

    @Transactional(readOnly = true)
    public PracticeReleaseProposalDTO practiceRelease(String slug) {
        return requireProposal(catalog.practice(slug));
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> accept(
            String slug,
            @Nullable EntityTagPrecondition precondition,
            Map<PracticeDefinitionField, PracticeReleaseChoice> choices) {
        lock.acquire();
        CatalogEntry<PracticeDefinition> before = catalog.practice(slug);
        PracticeReleaseProposalDTO proposal = requireProposal(before);
        PracticeReleasePrecondition.requireCurrent(precondition, proposal);
        PracticeDefinition merged =
                PracticeDefinitionMerge.apply(proposal.base(), proposal.current(), proposal.offered(), choices);
        CuratedCatalogModel.validatePracticeGroup(catalog.catalog(), merged);
        validator.validate(merged);
        if (merged.equals(proposal.offered())) {
            return catalog.resetPractice(slug, EntityTagPrecondition.parse('"' + before.etag() + '"'));
        }
        CuratedPracticeOverride override = overrides.findBySlug(slug).orElseThrow();
        override.acceptBundledRelease(merged, proposal.offered(), clock.instant());
        overrides.save(override);
        return catalog.recordPractice(slug, before);
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> decline(String slug, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        CatalogEntry<PracticeDefinition> before = catalog.practice(slug);
        PracticeReleaseProposalDTO proposal = requireProposal(before);
        PracticeReleasePrecondition.requireCurrent(precondition, proposal);
        CuratedPracticeOverride override = overrides.findBySlug(slug).orElseThrow();
        override.acknowledge(CuratedDefinitionDigest.of(slug, proposal.offered()), clock.instant());
        overrides.save(override);
        return catalog.recordPractice(slug, before);
    }

    /**
     * The instance hop of the one-shot practice program upgrade: each instance copy whose practice script is the one
     * its adopted base holds, while the bundled catalog now ships another, takes the shipped script. A copy whose
     * script was changed here keeps it, and keeps its normal offer. Every other field and every other pending offer
     * stays as it is.
     *
     * @return how many instance copies took the shipped script
     */
    @Transactional
    public int adoptShippedPracticeScripts() {
        lock.acquire();
        int adopted = 0;
        for (CuratedPracticeOverride override : overrides.findAll()) {
            PracticeDefinition current = override.definition();
            PracticeDefinition base = override.getAdoptedBase();
            if (current == null
                    || base == null
                    || override.getAdoptedBaseSource() == null
                    || override.getAdoptedBaseSource() == AdoptedBaseSource.CURRENT_DEFINITION) continue;
            CatalogEntry<PracticeDefinition> before =
                    catalog.catalog().practice(override.getSlug()).orElse(null);
            if (before == null) continue;
            PracticeDefinition shipped = before.shipped();
            if (shipped == null
                    || !Objects.equals(base.practiceScript(), current.practiceScript())
                    || Objects.equals(shipped.practiceScript(), current.practiceScript())) {
                continue;
            }
            override.adoptShippedPracticeScript(shipped, clock.instant());
            overrides.save(override);
            catalog.recordPractice(override.getSlug(), before);
            adopted++;
        }
        return adopted;
    }

    private PracticeReleaseProposalDTO requireProposal(CatalogEntry<PracticeDefinition> entry) {
        CuratedPracticeOverride override = overrides.findBySlug(entry.slug()).orElse(null);
        PracticeDefinition shipped = entry.shipped();
        PracticeDefinition base = override == null ? null : override.getAdoptedBase();
        if (override == null
                || shipped == null
                || base == null
                || base.equals(shipped)
                || CuratedDefinitionDigest.of(entry.slug(), shipped).equals(entry.acceptedBundledDigest())) {
            throw new EntityNotFoundException("Practice release", entry.slug());
        }
        return PracticeReleaseProposalDTO.of(
                entry.slug(),
                base,
                entry.effective(),
                shipped,
                Objects.requireNonNull(override.getAdoptedBaseSource()),
                null);
    }
}
