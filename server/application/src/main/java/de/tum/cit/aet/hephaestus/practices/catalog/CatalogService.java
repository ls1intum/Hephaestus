package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionMerge;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeReleaseChoice;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalogLoader;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnServerRole
public class CatalogService {
    private final CatalogEntryRepository entries;
    private final BundledPracticeCatalogLoader bundled;
    private final PracticeReviewDigest reviewDigest;
    private final PracticeDefinitionValidator validator;
    private final CatalogLock lock;
    private final JsonMapper mapper;
    private final ConfigAuditPort audit;

    public EffectiveCatalog effectiveCatalog() {
        var bundle = bundled.catalog();
        var groups = compose(
                CatalogEntryType.GROUP,
                bundle.groups(),
                GroupDefinition.class,
                value -> null,
                entry -> mapper.treeToValue(Objects.requireNonNull(entry.getDefinition()), GroupDefinition.class));
        var practices = compose(
                CatalogEntryType.PRACTICE,
                bundle.practices(),
                CatalogPracticeDocument.class,
                CatalogPracticeDocument::kind,
                this::localPractice);
        CanonicalDigest digest = new CanonicalDigest();
        groups.forEach(entry -> digest.add(entry.etag()));
        practices.forEach(entry -> digest.add(entry.etag()));
        return new EffectiveCatalog(groups, practices, digest.hex());
    }

    private <D> List<CatalogOffer<D>> compose(
            CatalogEntryType type,
            List<BundledPracticeCatalog.BundledEntry<D>> shipped,
            Class<D> shape,
            Function<D, @Nullable ItemKind> kind,
            Function<CatalogEntry, D> authoredDefinition) {
        Map<String, D> bundle = new LinkedHashMap<>();
        Map<String, Integer> positions = new HashMap<>();
        shipped.forEach(entry -> {
            bundle.put(entry.slug(), entry.definition());
            positions.put(entry.slug(), entry.position());
        });
        Map<String, CatalogEntry> local = new HashMap<>();
        entries.findByIdTypeOrderByIdSlugAsc(type)
                .forEach(entry -> local.put(entry.getId().slug(), entry));
        var slugs = new TreeSet<>(bundle.keySet());
        slugs.addAll(local.keySet());
        List<CatalogOffer<D>> offers = new ArrayList<>();
        int localPosition =
                positions.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        for (String slug : slugs) {
            CatalogEntry entry = local.get(slug);
            D original = bundle.get(slug);
            JsonNode authored = entry == null ? null : entry.getDefinition();
            D effective = authored == null ? original : authoredDefinition.apply(Objects.requireNonNull(entry));
            if (effective == null) continue;
            JsonNode baseNode = entry == null ? null : entry.getBase();
            D base = baseNode == null ? null : mapper.treeToValue(baseNode, shape);
            int position = entry != null && entry.getPosition() != null
                    ? entry.getPosition()
                    : positions.containsKey(slug) ? positions.get(slug) : localPosition++;
            boolean excluded = entry != null && entry.getExcludedAt() != null;
            CatalogEntryState state = authored == null
                    ? CatalogEntryState.FROM_HEPHAESTUS
                    : original == null
                            ? baseNode == null ? CatalogEntryState.YOURS : CatalogEntryState.NO_LONGER_SHIPPED
                            : Objects.equals(effective, original)
                                    ? CatalogEntryState.EDITED_HERE
                                    : Objects.equals(original, base)
                                            ? CatalogEntryState.EDITED_HERE
                                            : CatalogEntryState.UPDATE_WAITING;
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
                    .addNullable(baseNode == null ? null : mapper.writeValueAsString(base == null ? baseNode : base))
                    .addInt(position)
                    .addInt(excluded ? 1 : 0)
                    .add(entry == null ? "0" : Long.toString(entry.getVersion()))
                    .hex();
            offers.add(new CatalogOffer<>(
                    slug,
                    kind.apply(effective),
                    effective,
                    original,
                    base,
                    position,
                    excluded,
                    state,
                    change,
                    tag,
                    !excluded));
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

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> createPractice(@Valid CatalogPracticeInputDTO input) {
        lock.acquire();
        var catalog = effectiveCatalog();
        if (catalog.practice(input.slug()).isPresent())
            throw new CatalogConflictException("This catalog slug already exists.");
        return writePractice(catalog, input.slug(), input);
    }

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> updatePractice(
            String slug, @Valid CatalogPracticeInputDTO input, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        require(precondition, practice(catalog, slug).etag());
        return writePractice(catalog, slug, input);
    }

    private CatalogOffer<CatalogPracticeDocument> writePractice(
            EffectiveCatalog catalog, String slug, CatalogPracticeInputDTO input) {
        if (!slug.equals(input.slug())) throw new IllegalArgumentException("The catalog slug must match the route.");
        var current = catalog.practice(slug);
        ItemKind kind = new ItemKind(input.kind());
        if (current.isPresent() && !current.get().effective().kind().equals(kind))
            throw new IllegalArgumentException("A catalog practice keeps its item kind.");
        validator.validate(kind, input.definition());
        if (input.groupSlug() != null) group(catalog, input.groupSlug());
        var entry = authoredEntry(catalog, CatalogEntryType.PRACTICE, slug, input.position());
        entry.setKind(kind);
        entry.setDefinition(
                mapper.valueToTree(new CatalogPracticeDocument(kind, input.groupSlug(), input.definition()).content()));
        entries.saveAndFlush(entry);
        var result = practice(effectiveCatalog(), slug);
        record(CatalogEntryType.PRACTICE, slug, current.map(this::snapshot).orElse(null), snapshot(result));
        return result;
    }

    @Transactional
    public CatalogOffer<GroupDefinition> createGroup(@Valid CatalogGroupInputDTO input) {
        lock.acquire();
        var catalog = effectiveCatalog();
        if (catalog.group(input.slug()).isPresent())
            throw new CatalogConflictException("This catalog slug already exists.");
        return writeGroup(catalog, input.slug(), input);
    }

    @Transactional
    public CatalogOffer<GroupDefinition> updateGroup(
            String slug, @Valid CatalogGroupInputDTO input, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        require(precondition, group(catalog, slug).etag());
        return writeGroup(catalog, slug, input);
    }

    private CatalogOffer<GroupDefinition> writeGroup(
            EffectiveCatalog catalog, String slug, CatalogGroupInputDTO input) {
        if (!slug.equals(input.slug())) throw new IllegalArgumentException("The catalog slug must match the route.");
        var current = catalog.group(slug);
        var entry = authoredEntry(catalog, CatalogEntryType.GROUP, slug, input.position());
        entry.setDefinition(mapper.valueToTree(
                new GroupDefinition(input.name(), input.description(), input.icon(), input.color())));
        entries.saveAndFlush(entry);
        var result = group(effectiveCatalog(), slug);
        record(CatalogEntryType.GROUP, slug, current.map(this::snapshot).orElse(null), snapshot(result));
        return result;
    }

    private CatalogEntry authoredEntry(
            EffectiveCatalog catalog, CatalogEntryType type, String slug, @Nullable Integer position) {
        CatalogEntry entry = row(type, slug);
        var current = type == CatalogEntryType.PRACTICE ? catalog.practice(slug) : catalog.group(slug);
        if (entry.getBase() == null) {
            Object shipped = current.isEmpty() ? null : current.get().shipped();
            entry.setBase(shipped == null ? null : mapper.valueToTree(shipped));
        }
        entry.setPosition(
                position == null
                        ? current.map(CatalogOffer::position).orElseGet(() -> nextPosition(catalog, type))
                        : position);
        return entry;
    }

    @Transactional
    public void setExcluded(
            CatalogEntryType type, String slug, boolean excluded, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        var current = offer(catalog, type, slug);
        require(precondition, current.etag());
        CatalogSnapshot before = snapshot(current);
        CatalogEntry entry = row(type, slug);
        if (excluded != (entry.getExcludedAt() != null)) {
            entry.setExcludedAt(excluded ? Instant.now() : null);
            entries.saveAndFlush(entry);
            record(type, slug, before, snapshot(offer(effectiveCatalog(), type, slug)));
        }
    }

    @Transactional
    public void reset(CatalogEntryType type, String slug, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        var current = offer(catalog, type, slug);
        require(precondition, current.etag());
        CatalogSnapshot before = snapshot(current);
        CatalogEntry entry = row(type, slug);
        if (current.shipped() == null) {
            throw new IllegalArgumentException("This entry has no shipped definition to restore.");
        }
        if (type == CatalogEntryType.PRACTICE) {
            var practice = practice(catalog, slug);
            var shipped = Objects.requireNonNull(practice.shipped());
            if (!practice.effective().kind().equals(shipped.kind()))
                throw new CatalogConflictException("A catalog reset cannot change the practice's item kind.");
        }
        entry.setKind(null);
        entry.setDefinition(null);
        entry.setBase(null);
        entries.saveAndFlush(entry);
        record(type, slug, before, snapshot(offer(effectiveCatalog(), type, slug)));
    }

    @Transactional
    public void acknowledge(CatalogEntryType type, String slug, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        var current = offer(catalog, type, slug);
        require(precondition, current.etag());
        CatalogSnapshot before = snapshot(current);
        CatalogEntry entry = row(type, slug);
        Object shipped = current.shipped();
        if (shipped == null) throw new IllegalArgumentException("This entry has no shipped definition.");
        if (entry.getDefinition() != null) {
            entry.setBase(mapper.valueToTree(shipped));
            entries.saveAndFlush(entry);
            record(type, slug, before, snapshot(offer(effectiveCatalog(), type, slug)));
        }
    }

    @Transactional
    public void reorder(CatalogEntryType type, List<String> slugs, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        require(precondition, catalog.etag());
        List<String> existing = type == CatalogEntryType.PRACTICE
                ? catalog.practices().stream().map(CatalogOffer::slug).toList()
                : catalog.groups().stream().map(CatalogOffer::slug).toList();
        if (slugs.size() != Set.copyOf(slugs).size() || !Set.copyOf(slugs).equals(Set.copyOf(existing))) {
            throw new IllegalArgumentException("List every catalog entry once in the order.");
        }
        for (int position = 0; position < slugs.size(); position++) {
            CatalogEntry entry = row(type, slugs.get(position));
            entry.setPosition(position);
            entries.save(entry);
        }
        entries.flush();
    }

    @Transactional
    public void resetOrder(@Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        require(precondition, effectiveCatalog().etag());
        for (CatalogEntry entry : entries.findAll()) {
            entry.setPosition(null);
            entries.save(entry);
        }
        entries.flush();
    }

    private CatalogEntry row(CatalogEntryType type, String slug) {
        return entries.findById(new CatalogEntry.Id(type, slug)).orElseGet(() -> new CatalogEntry(type, slug));
    }

    private static CatalogOffer<?> offer(EffectiveCatalog catalog, CatalogEntryType type, String slug) {
        return type == CatalogEntryType.PRACTICE ? practice(catalog, slug) : group(catalog, slug);
    }

    private static CatalogOffer<CatalogPracticeDocument> practice(EffectiveCatalog catalog, String slug) {
        return catalog.practice(slug).orElseThrow(() -> new EntityNotFoundException("Catalog practice", slug));
    }

    private static CatalogOffer<GroupDefinition> group(EffectiveCatalog catalog, String slug) {
        return catalog.group(slug).orElseThrow(() -> new EntityNotFoundException("Catalog group", slug));
    }

    public static void require(@Nullable EntityTagPrecondition precondition, String tag) {
        if (precondition == null) throw new CatalogPreconditionRequiredException();
        if (!precondition.matchesExact(tag)) throw new StaleCatalogEntryException(tag);
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
                shipped == null
                        ? null
                        : new CatalogGroupDefinitionDTO(
                                shipped.name(), shipped.description(), shipped.icon(), shipped.color()),
                entry.position(),
                entry.excluded(),
                entry.state(),
                entry.changeKind(),
                entry.offered(),
                entry.etag());
    }

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> applyRelease(
            String slug,
            Map<PracticeDefinitionField, PracticeReleaseChoice> choices,
            @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        var current = practice(catalog, slug);
        require(precondition, current.etag());
        var shipped = current.shipped();
        if (shipped == null) throw new EntityNotFoundException("Catalog practice release", slug);
        var definition = PracticeDefinitionMerge.apply(
                current.base() == null ? null : current.base().definition(),
                current.effective().definition(),
                shipped.definition(),
                choices);
        validator.validate(current.effective().kind(), definition);
        var before = snapshot(current);
        var entry = row(CatalogEntryType.PRACTICE, slug);
        entry.setKind(current.effective().kind());
        entry.setDefinition(mapper.valueToTree(new CatalogPracticeDocument(
                        current.effective().kind(), current.effective().groupSlug(), definition)
                .content()));
        entry.setBase(mapper.valueToTree(shipped));
        entries.saveAndFlush(entry);
        var result = practice(slug);
        record(CatalogEntryType.PRACTICE, slug, before, snapshot(result));
        return result;
    }

    @Transactional
    public void move(
            String slug,
            @Nullable String groupSlug,
            @PositiveOrZero int position,
            @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var catalog = effectiveCatalog();
        require(precondition, catalog.etag());
        var current = practice(catalog, slug);
        var before = snapshot(current);
        if (groupSlug != null) group(catalog, groupSlug);
        CatalogEntry entry = row(CatalogEntryType.PRACTICE, slug);
        var document = current.effective();
        entry.setKind(document.kind());
        entry.setDefinition(mapper.valueToTree(
                new CatalogPracticeDocument(document.kind(), groupSlug, document.definition()).content()));
        if (entry.getBase() == null)
            entry.setBase(current.shipped() == null ? null : mapper.valueToTree(current.shipped()));
        entries.saveAndFlush(entry);
        var changed = effectiveCatalog();
        var peers = new ArrayList<>(changed.practices().stream()
                .filter(offer -> !offer.slug().equals(slug)
                        && Objects.equals(offer.effective().groupSlug(), groupSlug))
                .toList());
        peers.add(Math.min(position, peers.size()), practice(changed, slug));
        resequence(peers);
        if (!Objects.equals(document.groupSlug(), groupSlug)) {
            resequence(changed.practices().stream()
                    .filter(offer -> Objects.equals(offer.effective().groupSlug(), document.groupSlug()))
                    .toList());
        }
        entries.flush();
        record(CatalogEntryType.PRACTICE, slug, before, snapshot(practice(slug)));
    }

    private void resequence(List<CatalogOffer<CatalogPracticeDocument>> offers) {
        for (int i = 0; i < offers.size(); i++) {
            var entry = row(CatalogEntryType.PRACTICE, offers.get(i).slug());
            entry.setPosition(i);
            entries.save(entry);
        }
    }

    private static int nextPosition(EffectiveCatalog catalog, CatalogEntryType type) {
        return type == CatalogEntryType.PRACTICE
                ? catalog.practices().stream()
                                .mapToInt(CatalogOffer::position)
                                .max()
                                .orElse(-1)
                        + 1
                : catalog.groups().stream()
                                .mapToInt(CatalogOffer::position)
                                .max()
                                .orElse(-1)
                        + 1;
    }

    public String definitionDigest(CatalogOffer<?> offer) {
        return new CanonicalDigest()
                .add(mapper.writeValueAsString(offer.effective()))
                .hex();
    }

    private CatalogSnapshot snapshot(CatalogOffer<?> offer) {
        return new CatalogSnapshot(
                offer.slug(),
                definitionDigest(offer),
                offer.base() == null
                        ? null
                        : new CanonicalDigest()
                                .add(mapper.writeValueAsString(offer.base()))
                                .hex(),
                offer.position(),
                offer.excluded());
    }

    private void record(CatalogEntryType type, String slug, @Nullable CatalogSnapshot before, CatalogSnapshot after) {
        var entity = type == CatalogEntryType.PRACTICE
                ? ConfigAuditEntityType.CATALOG_PRACTICE
                : ConfigAuditEntityType.CATALOG_GROUP;
        audit.record(
                before == null
                        ? ConfigAuditEntry.instanceCreated(entity, slug, after)
                        : ConfigAuditEntry.instanceUpdated(entity, slug, before, after));
    }
}
