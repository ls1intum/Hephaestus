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
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeReviewDigest;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalogLoader;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedCatalogConflictException;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPreconditionRequiredException;
import de.tum.cit.aet.hephaestus.practices.curated.StaleCuratedEntryException;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupDefinitionDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogGroupInputDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@ConditionalOnServerRole
public class CatalogService {
    private final CatalogEntryRepository entries;
    private final BundledPracticeCatalogLoader bundled;
    private final PracticeReviewDigest reviewDigest;
    private final PracticeDefinitionValidator validator;
    private final CatalogLock lock;
    private final JsonMapper mapper;
    private final ConfigAuditPort audit;

    @Transactional(readOnly = true)
    public EffectiveCatalog effectiveCatalog() {
        var bundle = bundled.catalog();
        var groups = compose(CatalogEntryType.GROUP, bundle.groups(), GroupDefinition.class, value -> null);
        var practices = compose(
                CatalogEntryType.PRACTICE,
                bundle.practices(),
                CatalogPracticeDocument.class,
                CatalogPracticeDocument::kind);
        CanonicalDigest digest = new CanonicalDigest();
        groups.forEach(entry -> digest.add(entry.etag()));
        practices.forEach(entry -> digest.add(entry.etag()));
        return new EffectiveCatalog(groups, practices, digest.hex());
    }

    private <D> List<CatalogOffer<D>> compose(
            CatalogEntryType type,
            List<BundledPracticeCatalog.BundledEntry<D>> shipped,
            Class<D> shape,
            Function<D, @Nullable ItemKind> kind) {
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
            D effective = authored == null ? original : mapper.treeToValue(authored, shape);
            if (effective == null) continue;
            JsonNode baseNode = entry == null ? null : entry.getBase();
            D base = baseNode == null || baseNode.size() == 0 ? null : mapper.treeToValue(baseNode, shape);
            int position = entry != null && entry.getPosition() != null
                    ? entry.getPosition()
                    : positions.containsKey(slug) ? positions.get(slug) : localPosition++;
            if (type == CatalogEntryType.PRACTICE
                    && entry != null
                    && authored != null
                    && !Objects.equals(entry.getKind(), kind.apply(effective))) {
                throw new IllegalStateException("The catalog practice kind does not match its stored document.");
            }
            boolean excluded = entry != null && entry.getExcludedAt() != null;
            CatalogEntryState state = authored == null
                    ? CatalogEntryState.FROM_HEPHAESTUS
                    : original == null
                            ? baseNode == null || baseNode.size() == 0
                                    ? CatalogEntryState.YOURS
                                    : CatalogEntryState.NO_LONGER_SHIPPED
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
                    .addNullable(baseNode == null ? null : mapper.writeValueAsString(baseNode))
                    .addInt(position)
                    .addInt(excluded ? 1 : 0)
                    .add(entry == null ? "0" : Long.toString(entry.getVersion()))
                    .hex();
            offers.add(new CatalogOffer<>(
                    slug, kind.apply(effective), effective, original, base, position, excluded, state, change, tag));
        }
        return offers.stream()
                .sorted(Comparator.comparingInt(CatalogOffer<D>::position).thenComparing(CatalogOffer::slug))
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogOffer<CatalogPracticeDocument> practice(String slug) {
        return effectiveCatalog()
                .practice(slug)
                .orElseThrow(() -> new EntityNotFoundException("Catalog practice", slug));
    }

    @Transactional(readOnly = true)
    public CatalogOffer<GroupDefinition> group(String slug) {
        return effectiveCatalog().group(slug).orElseThrow(() -> new EntityNotFoundException("Catalog group", slug));
    }

    @Transactional
    public CatalogOffer<CatalogPracticeDocument> putPractice(
            String slug, CatalogPracticeInputDTO input, boolean create, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        if (!slug.equals(input.slug())) throw new IllegalArgumentException("The catalog slug must match the route.");
        var current = effectiveCatalog().practice(slug);
        if (create && current.isPresent())
            throw new CuratedCatalogConflictException("This catalog slug already exists.");
        if (!create)
            require(
                    precondition,
                    current.orElseThrow(() -> new EntityNotFoundException("Catalog practice", slug))
                            .etag());
        validateSlug(slug);
        position(input.position());
        ItemKind kind = new ItemKind(input.kind());
        if (current.isPresent() && !current.get().effective().kind().equals(kind))
            throw new IllegalArgumentException("A catalog practice keeps its item kind.");
        validator.validate(kind, input.definition());
        var document = new CatalogPracticeDocument(kind, input.groupSlug(), input.definition());
        if (input.groupSlug() != null) group(input.groupSlug());
        CatalogSnapshot before = current.map(this::snapshot).orElse(null);
        CatalogEntry entry = row(CatalogEntryType.PRACTICE, slug);
        entry.setKind(kind);
        entry.setDefinition(mapper.valueToTree(document));
        if (entry.getBase() == null)
            entry.setBase(current.flatMap(offer -> Optional.ofNullable(offer.shipped()))
                    .<JsonNode>map(mapper::valueToTree)
                    .orElseGet(() -> mapper.createObjectNode()));
        entry.setPosition(
                input.position() == null
                        ? current.map(CatalogOffer::position).orElseGet(() -> nextPosition(CatalogEntryType.PRACTICE))
                        : input.position());
        entries.saveAndFlush(entry);
        var result = practice(slug);
        record(CatalogEntryType.PRACTICE, slug, before, snapshot(result));
        return result;
    }

    @Transactional
    public CatalogOffer<GroupDefinition> putGroup(
            String slug, CatalogGroupInputDTO input, boolean create, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        if (!slug.equals(input.slug())) throw new IllegalArgumentException("The catalog slug must match the route.");
        var current = effectiveCatalog().group(slug);
        if (create && current.isPresent())
            throw new CuratedCatalogConflictException("This catalog slug already exists.");
        if (!create)
            require(
                    precondition,
                    current.orElseThrow(() -> new EntityNotFoundException("Catalog group", slug))
                            .etag());
        validateSlug(slug);
        position(input.position());
        var definition = new GroupDefinition(input.name(), input.description(), input.icon(), input.color());
        CatalogSnapshot before = current.map(this::snapshot).orElse(null);
        CatalogEntry entry = row(CatalogEntryType.GROUP, slug);
        entry.setDefinition(mapper.valueToTree(definition));
        if (entry.getBase() == null)
            entry.setBase(current.flatMap(offer -> Optional.ofNullable(offer.shipped()))
                    .<JsonNode>map(mapper::valueToTree)
                    .orElseGet(() -> mapper.createObjectNode()));
        entry.setPosition(
                input.position() == null
                        ? current.map(CatalogOffer::position).orElseGet(() -> nextPosition(CatalogEntryType.GROUP))
                        : input.position());
        entries.saveAndFlush(entry);
        var result = group(slug);
        record(CatalogEntryType.GROUP, slug, before, snapshot(result));
        return result;
    }

    @Transactional
    public void setExcluded(
            CatalogEntryType type, String slug, boolean excluded, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        require(precondition, etag(type, slug));
        CatalogSnapshot before = snapshot(type, slug);
        CatalogEntry entry = row(type, slug);
        if (excluded != (entry.getExcludedAt() != null)) {
            entry.setExcludedAt(excluded ? Instant.now() : null);
            entries.saveAndFlush(entry);
            record(type, slug, before, snapshot(type, slug));
        }
    }

    @Transactional
    public void reset(CatalogEntryType type, String slug, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        require(precondition, etag(type, slug));
        CatalogSnapshot before = snapshot(type, slug);
        CatalogEntry entry = row(type, slug);
        if ((type == CatalogEntryType.PRACTICE && practice(slug).shipped() == null)
                || (type == CatalogEntryType.GROUP && group(slug).shipped() == null)) {
            throw new IllegalArgumentException("This entry has no shipped definition to restore.");
        }
        if (type == CatalogEntryType.PRACTICE) {
            var current = practice(slug);
            var shipped = Objects.requireNonNull(current.shipped());
            if (!current.effective().kind().equals(shipped.kind()))
                throw new CuratedCatalogConflictException("A catalog reset cannot change the practice's item kind.");
        }
        entry.setKind(null);
        entry.setDefinition(null);
        entry.setBase(null);
        entries.saveAndFlush(entry);
        record(type, slug, before, snapshot(type, slug));
    }

    @Transactional
    public void acknowledge(CatalogEntryType type, String slug, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        require(precondition, etag(type, slug));
        CatalogSnapshot before = snapshot(type, slug);
        CatalogEntry entry = row(type, slug);
        Object shipped = type == CatalogEntryType.PRACTICE
                ? practice(slug).shipped()
                : group(slug).shipped();
        if (shipped == null) throw new IllegalArgumentException("This entry has no shipped definition.");
        if (entry.getDefinition() != null) {
            entry.setBase(mapper.valueToTree(shipped));
            entries.saveAndFlush(entry);
            record(type, slug, before, snapshot(type, slug));
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

    private String etag(CatalogEntryType type, String slug) {
        return type == CatalogEntryType.PRACTICE
                ? practice(slug).etag()
                : group(slug).etag();
    }

    public static void require(@Nullable EntityTagPrecondition precondition, String tag) {
        if (precondition == null) throw new CuratedPreconditionRequiredException();
        if (!precondition.matchesExact(tag)) throw new StaleCuratedEntryException(tag);
    }

    @Transactional(readOnly = true)
    public CatalogDTO view() {
        var catalog = effectiveCatalog();
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
                entry.offered()
                        && (document.groupSlug() == null
                                || effectiveCatalog()
                                        .group(document.groupSlug())
                                        .map(CatalogOffer::offered)
                                        .orElse(false)),
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
            String slug, PracticeDefinition definition, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        var current = practice(slug);
        require(precondition, current.etag());
        var shipped = current.shipped();
        if (shipped == null) throw new EntityNotFoundException("Catalog practice release", slug);
        validator.validate(current.effective().kind(), definition);
        var before = snapshot(current);
        var entry = row(CatalogEntryType.PRACTICE, slug);
        entry.setKind(current.effective().kind());
        entry.setDefinition(mapper.valueToTree(new CatalogPracticeDocument(
                current.effective().kind(), current.effective().groupSlug(), definition)));
        entry.setBase(mapper.valueToTree(shipped));
        entries.saveAndFlush(entry);
        var result = practice(slug);
        record(CatalogEntryType.PRACTICE, slug, before, snapshot(result));
        return result;
    }

    @Transactional
    public void place(
            String slug, @Nullable String groupSlug, int position, @Nullable EntityTagPrecondition precondition) {
        lock.acquire();
        require(precondition, effectiveCatalog().etag());
        var current = practice(slug);
        var before = snapshot(current);
        if (position < 0) throw new IllegalArgumentException("Use a nonnegative position.");
        if (groupSlug != null) group(groupSlug);
        CatalogEntry entry = row(CatalogEntryType.PRACTICE, slug);
        var document = current.effective();
        entry.setKind(document.kind());
        entry.setDefinition(
                mapper.valueToTree(new CatalogPracticeDocument(document.kind(), groupSlug, document.definition())));
        if (entry.getBase() == null)
            entry.setBase(
                    current.shipped() == null ? mapper.createObjectNode() : mapper.valueToTree(current.shipped()));
        entries.saveAndFlush(entry);
        var peers = new ArrayList<>(effectiveCatalog().practices().stream()
                .filter(offer -> !offer.slug().equals(slug)
                        && Objects.equals(offer.effective().groupSlug(), groupSlug))
                .toList());
        peers.add(Math.min(position, peers.size()), practice(slug));
        resequence(peers);
        if (!Objects.equals(document.groupSlug(), groupSlug)) {
            resequence(effectiveCatalog().practices().stream()
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

    private int nextPosition(CatalogEntryType type) {
        var catalog = effectiveCatalog();
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

    private static void validateSlug(String slug) {
        if (slug.length() > 64 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Use a lowercase catalog slug with single hyphens.");
    }

    private static void position(@Nullable Integer position) {
        if (position != null && position < 0) throw new IllegalArgumentException("Use a nonnegative position.");
    }

    private CatalogSnapshot snapshot(CatalogEntryType type, String slug) {
        return type == CatalogEntryType.PRACTICE ? snapshot(practice(slug)) : snapshot(group(slug));
    }

    private CatalogSnapshot snapshot(CatalogOffer<?> offer) {
        return new CatalogSnapshot(
                offer.slug(),
                new CanonicalDigest()
                        .add(mapper.writeValueAsString(offer.effective()))
                        .hex(),
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
                ? ConfigAuditEntityType.CURATED_PRACTICE
                : ConfigAuditEntityType.CURATED_PRACTICE_GROUP;
        audit.record(
                before == null
                        ? ConfigAuditEntry.instanceCreated(entity, slug, after)
                        : ConfigAuditEntry.instanceUpdated(entity, slug, before, after));
    }
}
