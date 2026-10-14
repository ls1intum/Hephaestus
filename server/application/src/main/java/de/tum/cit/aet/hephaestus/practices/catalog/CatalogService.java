package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionMerge;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalogLoader;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseFieldDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeReleaseProposalDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnServerRole
public class CatalogService {
    private final CatalogEntryRepository entries;
    private final BundledPracticeCatalogLoader bundled;
    private final PracticeReviewDigest reviewDigest;
    private final JsonMapper mapper;

    public EffectiveCatalog effectiveCatalog() {
        var bundle = bundled.catalog();
        var groups = compose(
                CatalogEntryType.GROUP,
                bundle.groups(),
                GroupDefinition.class,
                entry -> mapper.treeToValue(Objects.requireNonNull(entry.getDefinition()), GroupDefinition.class),
                Set.of());
        Set<String> offeredGroups = groups.stream()
                .filter(CatalogOffer::offered)
                .map(CatalogOffer::slug)
                .collect(Collectors.toUnmodifiableSet());
        var practices = compose(
                CatalogEntryType.PRACTICE,
                bundle.practices(),
                CatalogPracticeDocument.class,
                this::localPractice,
                offeredGroups);
        CanonicalDigest digest = new CanonicalDigest();
        groups.forEach(entry -> digest.add(entry.etag()));
        practices.forEach(entry -> digest.add(entry.etag()));
        return new EffectiveCatalog(groups, practices, digest.hex());
    }

    private <D> List<CatalogOffer<D>> compose(
            CatalogEntryType type,
            List<BundledPracticeCatalog.BundledEntry<D>> shipped,
            Class<D> shape,
            Function<CatalogEntry, D> authoredDefinition,
            Set<String> offeredGroups) {
        Map<String, D> bundle = new LinkedHashMap<>();
        Map<String, Integer> positions = new HashMap<>();
        Map<@Nullable String, Integer> nextPositions = new HashMap<>();
        shipped.forEach(entry -> {
            bundle.put(entry.slug(), entry.definition());
            positions.put(entry.slug(), entry.position());
            String groupSlug =
                    entry.definition() instanceof CatalogPracticeDocument document ? document.groupSlug() : null;
            nextPositions.merge(groupSlug, entry.position() + 1, Math::max);
        });
        Map<String, CatalogEntry> local = new HashMap<>();
        entries.findByIdTypeOrderByIdSlugAsc(type)
                .forEach(entry -> local.put(entry.getId().slug(), entry));
        var slugs = new TreeSet<>(bundle.keySet());
        slugs.addAll(local.keySet());
        List<CatalogOffer<D>> offers = new ArrayList<>();
        for (String slug : slugs) {
            CatalogEntry entry = local.get(slug);
            D original = bundle.get(slug);
            JsonNode authored = entry == null ? null : entry.getDefinition();
            D effective = authored == null ? original : authoredDefinition.apply(Objects.requireNonNull(entry));
            if (effective == null) continue;
            JsonNode baseNode = entry == null ? null : entry.getBase();
            D base = baseNode == null ? null : mapper.treeToValue(baseNode, shape);
            int position;
            if (entry != null && entry.getPosition() != null) {
                position = entry.getPosition();
            } else if (positions.containsKey(slug)) {
                position = positions.get(slug);
            } else {
                String groupSlug = effective instanceof CatalogPracticeDocument document ? document.groupSlug() : null;
                position = nextPositions.getOrDefault(groupSlug, 0);
                nextPositions.put(groupSlug, position + 1);
            }
            boolean excluded = entry != null && entry.getExcludedAt() != null;
            CatalogEntryState state;
            if (authored == null) {
                state = CatalogEntryState.FROM_HEPHAESTUS;
            } else if (original == null) {
                state = baseNode == null ? CatalogEntryState.YOURS : CatalogEntryState.NO_LONGER_SHIPPED;
            } else if (Objects.equals(effective, original) || Objects.equals(original, base)) {
                state = CatalogEntryState.EDITED_HERE;
            } else {
                state = CatalogEntryState.UPDATE_WAITING;
            }
            CatalogChangeKind change = CatalogChangeKind.NONE;
            if (original != null && !Objects.equals(effective, original)) {
                if (effective instanceof CatalogPracticeDocument current
                        && original instanceof CatalogPracticeDocument offered) {
                    change = reviewDigest.of(current.definition()).equals(reviewDigest.of(offered.definition()))
                            ? CatalogChangeKind.WORDING
                            : CatalogChangeKind.REVIEW;
                } else change = CatalogChangeKind.PRESENTATION;
            }
            String tag = new CanonicalDigest()
                    .add(slug)
                    .add(mapper.writeValueAsString(effective))
                    .addNullable(original == null ? null : mapper.writeValueAsString(original))
                    .addNullable(baseNode == null ? null : mapper.writeValueAsString(base))
                    .addInt(position)
                    .addInt(excluded ? 1 : 0)
                    .hex();
            offers.add(new CatalogOffer<>(
                    slug,
                    effective,
                    original,
                    base,
                    position,
                    excluded,
                    state,
                    change,
                    tag,
                    !excluded
                            && (!(effective instanceof CatalogPracticeDocument document)
                                    || document.groupSlug() == null
                                    || offeredGroups.contains(document.groupSlug()))));
        }
        return offers.stream()
                .sorted(Comparator.comparingInt(CatalogOffer<D>::position).thenComparing(CatalogOffer::slug))
                .toList();
    }

    private CatalogPracticeDocument localPractice(CatalogEntry entry) {
        var content = mapper.treeToValue(
                Objects.requireNonNull(entry.getDefinition()), CatalogPracticeDocument.Content.class);
        return new CatalogPracticeDocument(
                Objects.requireNonNull(entry.getKind()), content.groupSlug(), content.definition());
    }

    public CatalogOffer<CatalogPracticeDocument> practice(String slug) {
        return effectiveCatalog()
                .practice(slug)
                .orElseThrow(() -> new EntityNotFoundException("Catalog practice", slug));
    }

    public CatalogOffer<GroupDefinition> group(String slug) {
        return effectiveCatalog().group(slug).orElseThrow(() -> new EntityNotFoundException("Catalog group", slug));
    }

    public CatalogDTO view() {
        return view(effectiveCatalog());
    }

    public CatalogDTO view(EffectiveCatalog catalog) {
        return new CatalogDTO(
                catalog.practices().stream().map(this::practiceView).toList(),
                catalog.groups().stream().map(this::groupView).toList());
    }

    public CatalogPracticeDTO practiceView(CatalogOffer<CatalogPracticeDocument> entry) {
        var document = entry.effective();
        return new CatalogPracticeDTO(
                entry.slug(),
                document.kind().value(),
                document.groupSlug(),
                document.definition(),
                entry.shipped() == null ? null : entry.shipped().definition(),
                entry.position(),
                entry.excluded(),
                entry.state(),
                entry.changeKind(),
                entry.offered(),
                entry.etag());
    }

    public CatalogGroupDTO groupView(CatalogOffer<GroupDefinition> entry) {
        var definition = entry.effective();
        var shipped = entry.shipped();
        return new CatalogGroupDTO(
                entry.slug(),
                definition.name(),
                definition.description(),
                definition.icon(),
                definition.color(),
                shipped,
                entry.position(),
                entry.excluded(),
                entry.state(),
                entry.changeKind(),
                entry.offered(),
                entry.etag());
    }

    public PracticeReleaseProposalDTO proposal(String slug) {
        var entry = practice(slug);
        var shipped = entry.shipped();
        if (shipped == null) throw new EntityNotFoundException("Catalog practice release", slug);
        return new PracticeReleaseProposalDTO(
                slug,
                null,
                entry.effective().definition(),
                entry.base() == null ? null : entry.base().definition(),
                shipped.definition(),
                reviewDigest.of(shipped.definition()),
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
}
