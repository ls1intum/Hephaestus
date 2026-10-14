package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogPracticeDocument;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogOriginDTO;
import de.tum.cit.aet.hephaestus.practices.dto.FeedbackModeAssignmentDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeGroupDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.feedbackmode.FeedbackModeResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnServerRole
public class CatalogOriginPresenter {
    private final CatalogService catalog;
    private final PracticeVersionService versions;
    private final WorkspaceReviewDefaultsProvider defaults;

    public PracticeDTO present(Long workspaceId, Practice practice) {
        var effective = FeedbackModeResolver.resolvePractice(practice, defaults.rawDefaultFeedbackMode(workspaceId));
        return new PracticeDTO(
                practice.getId().toString(),
                practice.getSlug(),
                practice.getKind().value(),
                versions.viewCurrent(practice),
                practice.getGroup() == null ? null : practice.getGroup().getSlug(),
                practice.getPosition(),
                new FeedbackModeAssignmentDTO(practice.getFeedbackMode(), effective.mode(), effective.source()),
                origin(practice),
                practice.getCreatedAt(),
                practice.getUpdatedAt());
    }

    public PracticeGroupDTO present(Long workspaceId, PracticeGroup group) {
        var effective = FeedbackModeResolver.resolveGroup(group, defaults.rawDefaultFeedbackMode(workspaceId));
        String source = group.getCatalogSlug();
        var offer =
                source == null ? null : catalog.effectiveCatalog().group(source).orElse(null);
        var current = new GroupDefinition(group.getName(), group.getDescription(), group.getIcon(), group.getColor());
        CatalogEntryState state = source == null
                ? CatalogEntryState.YOURS
                : offer == null
                        ? CatalogEntryState.NO_LONGER_SHIPPED
                        : Objects.equals(current, offer.effective())
                                ? CatalogEntryState.FROM_HEPHAESTUS
                                : Objects.equals(group.getCatalogBase(), offer.effective())
                                        ? CatalogEntryState.EDITED_HERE
                                        : CatalogEntryState.UPDATE_WAITING;
        return new PracticeGroupDTO(
                group.getId().toString(),
                group.getSlug(),
                group.getName(),
                group.getDescription(),
                group.getIcon(),
                group.getColor(),
                group.getPosition(),
                group.isShownToDevelopers(),
                new FeedbackModeAssignmentDTO(group.getFeedbackMode(), effective.mode(), effective.source()),
                new CatalogOriginDTO(source, state, List.of(), offer == null ? null : offer.etag()),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }

    public List<PracticeDTO> presentPractices(Long workspaceId, List<Practice> practices) {
        return practices.stream()
                .map(practice -> present(workspaceId, practice))
                .toList();
    }

    public List<PracticeGroupDTO> presentGroups(Long workspaceId, List<PracticeGroup> groups) {
        return groups.stream().map(group -> present(workspaceId, group)).toList();
    }

    private CatalogOriginDTO origin(Practice practice) {
        String source = practice.getCatalogSlug();
        CatalogPracticeDocument base = versions.catalogBaseOf(practice);
        if (source == null) return new CatalogOriginDTO(null, CatalogEntryState.YOURS, List.of(), null);
        var current = versions.definitionOf(practice.getCurrentVersion());
        var edits = Arrays.stream(PracticeDefinitionField.values())
                .filter(field -> base == null
                        || !Objects.equals(
                                PracticeDefinitionMerge.value(field, base.definition()),
                                PracticeDefinitionMerge.value(field, current)))
                .toList();
        var offer = catalog.effectiveCatalog().practice(source).orElse(null);
        CatalogEntryState state = offer == null
                ? CatalogEntryState.NO_LONGER_SHIPPED
                : current.equals(offer.effective().definition())
                        ? CatalogEntryState.FROM_HEPHAESTUS
                        : Objects.equals(base, offer.effective())
                                ? CatalogEntryState.EDITED_HERE
                                : CatalogEntryState.UPDATE_WAITING;
        return new CatalogOriginDTO(
                source, state, base == null ? List.of() : edits, offer == null ? null : offer.etag());
    }
}
