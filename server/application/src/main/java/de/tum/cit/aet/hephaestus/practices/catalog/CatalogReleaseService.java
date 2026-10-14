package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionMerge;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeReleaseProposalDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseFieldDTO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnServerRole
public class CatalogReleaseService {
    private final CatalogService catalog;
    private final PracticeReviewDigest digest;

    public CatalogPracticeReleaseProposalDTO proposal(String slug) {
        var entry = catalog.practice(slug);
        var shipped = entry.shipped();
        if (shipped == null) throw new EntityNotFoundException("Catalog practice release", slug);
        return new CatalogPracticeReleaseProposalDTO(
                slug,
                entry.effective().definition(),
                entry.base() == null ? null : entry.base().definition(),
                shipped.definition(),
                digest.of(shipped.definition()),
                entry.etag(),
                PracticeDefinitionMerge.changes(
                                entry.base() == null ? null : entry.base().definition(),
                                entry.effective().definition(),
                                shipped.definition())
                        .stream()
                        .map(change -> new PracticeReleaseFieldDTO(
                                change.field(), change.changedLocally(), change.offeredChanged(), change.conflict()))
                        .toList());
    }

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> accept(
            String slug,
            Map<PracticeDefinitionField, PracticeReleaseChoice> choices,
            @Nullable EntityTagPrecondition precondition) {
        return catalog.applyRelease(slug, choices, precondition);
    }

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> decline(String slug, @Nullable EntityTagPrecondition precondition) {
        catalog.acknowledge(CatalogEntryType.PRACTICE, slug, precondition);
        return catalog.practice(slug);
    }
}
