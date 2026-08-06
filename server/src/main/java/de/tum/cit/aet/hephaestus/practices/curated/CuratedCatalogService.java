package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("The instance catalog is global")
public class CuratedCatalogService {

    private static final String CATALOG_PRACTICE = "Catalog practice";
    private static final String CATALOG_AREA = "Catalog area";

    private final BundledPracticeCatalogLoader loader;
    private final CuratedCatalogLock catalogLock;
    private final CuratedPracticeOverrideRepository practiceOverrides;
    private final CuratedAreaOverrideRepository areaOverrides;
    private final ConfigAuditPort configAudit;
    private final Clock clock;
    private final PracticeDefinitionValidator definitionValidator;

    @Transactional(readOnly = true)
    public EffectiveCatalog catalog() {
        return CuratedCatalogModel.compose(loader.catalog(), areaOverrides.findAll(), practiceOverrides.findAll());
    }

    @Transactional(readOnly = true)
    public CatalogEntry<PracticeDefinition> practice(String slug) {
        return catalog()
            .practice(slug)
            .orElseThrow(() -> new EntityNotFoundException(CATALOG_PRACTICE, slug));
    }

    @Transactional(readOnly = true)
    public CatalogEntry<AreaDefinition> area(String slug) {
        return catalog()
            .area(slug)
            .orElseThrow(() -> new EntityNotFoundException(CATALOG_AREA, slug));
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> writePractice(
        String slug,
        @Nullable EntityTagPrecondition precondition,
        PracticeDefinition definition
    ) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.validatePracticeArea(before, definition);
        definitionValidator.validate(definition);
        CatalogEntry<PracticeDefinition> entry = CuratedCatalogModel.requireEntry(
            before.practice(slug),
            CATALOG_PRACTICE,
            slug,
            precondition
        );
        if (entry.overridden() != null && definition.equals(entry.shipped())) {
            clearPracticeDefinition(slug);
            return recordPractice(slug, entry);
        }
        if (entry.effective().equals(definition)) {
            return entry;
        }
        CuratedPracticeOverride override = practiceOverrides
            .findBySlug(slug)
            .orElseGet(() -> new CuratedPracticeOverride(slug, clock.instant()));
        override.write(definition, CuratedCatalogModel.digestOf(entry.shipped(), slug), clock.instant());
        practiceOverrides.save(override);
        return recordPractice(slug, entry);
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> createPractice(String slug, PracticeDefinition definition) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.validatePracticeArea(before, definition);
        definitionValidator.validate(definition);
        if (before.practice(slug).isPresent()) {
            throw new CuratedCatalogConflictException("A practice with slug '" + slug + "' already exists.");
        }
        Instant now = clock.instant();
        CuratedPracticeOverride override = new CuratedPracticeOverride(slug, now);
        override.write(definition, null, now);
        practiceOverrides.save(override);
        CatalogEntry<PracticeDefinition> created = practice(slug);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                created.slug(),
                CuratedPracticeSnapshot.of(created)
            )
        );
        return created;
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> resetPractice(String slug, @Nullable EntityTagPrecondition precondition) {
        lockCatalog();
        CatalogEntry<PracticeDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().practice(slug),
            CATALOG_PRACTICE,
            slug,
            precondition
        );
        if (entry.shipped() == null) {
            throw new CuratedCatalogConflictException("Hephaestus ships no definition for '" + slug + "'.");
        }
        practiceOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.clearDefinition(clock.instant());
                if (override.isEmpty()) {
                    practiceOverrides.delete(override);
                } else {
                    practiceOverrides.save(override);
                }
            });
        return recordPractice(slug, entry);
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> keepPractice(String slug, @Nullable EntityTagPrecondition precondition) {
        lockCatalog();
        CatalogEntry<PracticeDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().practice(slug),
            CATALOG_PRACTICE,
            slug,
            precondition
        );
        if (entry.overridden() == null) {
            return entry;
        }
        practiceOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.acknowledge(CuratedCatalogModel.digestOf(entry.shipped(), slug), clock.instant());
                practiceOverrides.save(override);
            });
        return recordPractice(slug, entry);
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> setPracticeStatus(
        String slug,
        @Nullable EntityTagPrecondition precondition,
        CuratedStatus status
    ) {
        lockCatalog();
        CatalogEntry<PracticeDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().practice(slug),
            CATALOG_PRACTICE,
            slug,
            precondition
        );
        if (entry.retired() == (status == CuratedStatus.RETIRED)) {
            return entry;
        }
        CuratedPracticeOverride override = practiceOverrides
            .findBySlug(slug)
            .orElseGet(() -> new CuratedPracticeOverride(slug, clock.instant()));
        override.setStatus(status, clock.instant());
        if (override.isEmpty()) {
            practiceOverrides.delete(override);
        } else {
            practiceOverrides.save(override);
        }
        return recordPractice(slug, entry);
    }

    @Transactional
    public CatalogEntry<AreaDefinition> writeArea(
        String slug,
        @Nullable EntityTagPrecondition precondition,
        AreaDefinition definition
    ) {
        lockCatalog();
        CatalogEntry<AreaDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().area(slug),
            CATALOG_AREA,
            slug,
            precondition
        );
        if (entry.overridden() != null && definition.equals(entry.shipped())) {
            clearAreaDefinition(slug);
            return recordArea(slug, entry);
        }
        if (entry.effective().equals(definition)) {
            return entry;
        }
        CuratedAreaOverride override = areaOverrides
            .findBySlug(slug)
            .orElseGet(() -> new CuratedAreaOverride(slug, clock.instant()));
        override.write(definition, CuratedCatalogModel.digestOf(entry.shipped(), slug), clock.instant());
        areaOverrides.save(override);
        return recordArea(slug, entry);
    }

    @Transactional
    public CatalogEntry<AreaDefinition> createArea(String slug, AreaDefinition definition) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        if (before.area(slug).isPresent()) {
            throw new CuratedCatalogConflictException("An area with slug '" + slug + "' already exists.");
        }
        Instant now = clock.instant();
        CuratedAreaOverride override = new CuratedAreaOverride(slug, now);
        override.write(definition, null, now);
        areaOverrides.save(override);
        CatalogEntry<AreaDefinition> created = area(slug);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE_AREA,
                created.slug(),
                CuratedAreaSnapshot.of(created)
            )
        );
        return created;
    }

    @Transactional
    public CatalogEntry<AreaDefinition> resetArea(String slug, @Nullable EntityTagPrecondition precondition) {
        lockCatalog();
        CatalogEntry<AreaDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().area(slug),
            CATALOG_AREA,
            slug,
            precondition
        );
        if (entry.shipped() == null) {
            throw new CuratedCatalogConflictException("Hephaestus ships no definition for '" + slug + "'.");
        }
        areaOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.clearDefinition(clock.instant());
                if (override.isEmpty()) {
                    areaOverrides.delete(override);
                } else {
                    areaOverrides.save(override);
                }
            });
        return recordArea(slug, entry);
    }

    @Transactional
    public CatalogEntry<AreaDefinition> keepArea(String slug, @Nullable EntityTagPrecondition precondition) {
        lockCatalog();
        CatalogEntry<AreaDefinition> entry = CuratedCatalogModel.requireEntry(
            catalog().area(slug),
            CATALOG_AREA,
            slug,
            precondition
        );
        if (entry.overridden() == null) {
            return entry;
        }
        areaOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.acknowledge(CuratedCatalogModel.digestOf(entry.shipped(), slug), clock.instant());
                areaOverrides.save(override);
            });
        return recordArea(slug, entry);
    }

    @Transactional
    public EffectiveCatalog setAreaStatus(
        String slug,
        @Nullable EntityTagPrecondition precondition,
        CuratedStatus status
    ) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.requireCatalog(before, precondition);
        CatalogEntry<AreaDefinition> entry = before
            .area(slug)
            .orElseThrow(() -> new EntityNotFoundException(CATALOG_AREA, slug));
        if (entry.retired() == (status == CuratedStatus.RETIRED)) {
            return before;
        }
        CuratedAreaOverride override = areaOverrides
            .findBySlug(slug)
            .orElseGet(() -> new CuratedAreaOverride(slug, clock.instant()));
        override.setStatus(status, clock.instant());
        if (override.isEmpty()) {
            areaOverrides.delete(override);
        } else {
            areaOverrides.save(override);
        }
        recordArea(slug, entry);
        return catalog();
    }

    @Transactional
    public EffectiveCatalog reorderAreas(@Nullable EntityTagPrecondition precondition, List<String> orderedSlugs) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.requireCatalog(before, precondition);
        CuratedCatalogModel.validateCompleteOrder(
            before.areas().stream().map(CatalogEntry::slug).toList(),
            orderedSlugs,
            CATALOG_AREA
        );
        if (before.areas().stream().map(CatalogEntry::slug).toList().equals(orderedSlugs)) {
            return before;
        }
        Instant now = clock.instant();
        for (int position = 0; position < orderedSlugs.size(); position++) {
            CuratedAreaOverride override = areaOverride(orderedSlugs.get(position), now);
            override.setPosition(position, now);
            areaOverrides.save(override);
        }
        return catalog();
    }

    @Transactional
    public EffectiveCatalog reorderPractices(
        @Nullable EntityTagPrecondition precondition,
        @Nullable String areaSlug,
        List<String> orderedSlugs
    ) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.requireCatalog(before, precondition);
        if (areaSlug != null && before.area(areaSlug).isEmpty()) {
            throw new EntityNotFoundException(CATALOG_AREA, areaSlug);
        }
        List<String> existing = CuratedCatalogModel.practicesIn(before, areaSlug)
            .stream()
            .map(CatalogEntry::slug)
            .toList();
        CuratedCatalogModel.validateCompleteOrder(existing, orderedSlugs, CATALOG_PRACTICE);
        if (existing.equals(orderedSlugs)) {
            return before;
        }
        resequencePractices(orderedSlugs, clock.instant());
        return catalog();
    }

    @Transactional
    public EffectiveCatalog resetOrder(@Nullable EntityTagPrecondition precondition) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.requireCatalog(before, precondition);
        if (!before.customOrder()) {
            return before;
        }
        Instant now = clock.instant();
        practiceOverrides
            .findAll()
            .stream()
            .filter(override -> override.getPosition() != null)
            .forEach(override -> {
                override.clearPosition(now);
                if (override.isEmpty()) {
                    practiceOverrides.delete(override);
                } else {
                    practiceOverrides.save(override);
                }
            });
        areaOverrides
            .findAll()
            .stream()
            .filter(override -> override.getPosition() != null)
            .forEach(override -> {
                override.clearPosition(now);
                if (override.isEmpty()) {
                    areaOverrides.delete(override);
                } else {
                    areaOverrides.save(override);
                }
            });
        return catalog();
    }

    @Transactional
    public EffectiveCatalog placePractice(
        String slug,
        @Nullable EntityTagPrecondition precondition,
        @Nullable String areaSlug,
        int position
    ) {
        lockCatalog();
        EffectiveCatalog before = catalog();
        CuratedCatalogModel.requireCatalog(before, precondition);
        CatalogEntry<PracticeDefinition> entry = before
            .practice(slug)
            .orElseThrow(() -> new EntityNotFoundException(CATALOG_PRACTICE, slug));
        if (areaSlug != null && before.area(areaSlug).isEmpty()) {
            throw new EntityNotFoundException(CATALOG_AREA, areaSlug);
        }
        String sourceAreaSlug = entry.effective().areaSlug();
        if (Objects.equals(sourceAreaSlug, areaSlug)) {
            throw new IllegalArgumentException("Use the reorder endpoint to move a practice within its area");
        }
        List<String> source = CuratedCatalogModel.practicesIn(before, sourceAreaSlug)
            .stream()
            .map(CatalogEntry::slug)
            .filter(candidate -> !candidate.equals(slug))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String> target = CuratedCatalogModel.practicesIn(before, areaSlug)
            .stream()
            .map(CatalogEntry::slug)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (position < 0 || position > target.size()) {
            throw new IllegalArgumentException("position exceeds the destination size");
        }
        target.add(position, slug);

        Instant now = clock.instant();
        PracticeDefinition definition = entry.effective();
        PracticeDefinition moved = new PracticeDefinition(
            definition.name(),
            definition.artifactKind(),
            definition.triggerEvents(),
            definition.criteria(),
            definition.precomputeScript(),
            definition.automatedReviewPolicy(),
            definition.whyItMatters(),
            definition.whatGoodLooksLike(),
            areaSlug
        );
        CuratedPracticeOverride override = practiceOverride(slug, now);
        override.write(moved, CuratedCatalogModel.digestOf(entry.shipped(), slug), now);
        practiceOverrides.save(override);
        resequencePractices(source, now);
        resequencePractices(target, now);
        recordPractice(slug, entry);
        return catalog();
    }

    private CatalogEntry<PracticeDefinition> recordPractice(String slug, CatalogEntry<PracticeDefinition> before) {
        CatalogEntry<PracticeDefinition> after = practice(slug);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                slug,
                CuratedPracticeSnapshot.of(before),
                CuratedPracticeSnapshot.of(after)
            )
        );
        return after;
    }

    private CatalogEntry<AreaDefinition> recordArea(String slug, CatalogEntry<AreaDefinition> before) {
        CatalogEntry<AreaDefinition> after = area(slug);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE_AREA,
                slug,
                CuratedAreaSnapshot.of(before),
                CuratedAreaSnapshot.of(after)
            )
        );
        return after;
    }

    private void lockCatalog() {
        catalogLock.acquire();
    }

    private CuratedPracticeOverride practiceOverride(String slug, Instant now) {
        return practiceOverrides.findBySlug(slug).orElseGet(() -> new CuratedPracticeOverride(slug, now));
    }

    private CuratedAreaOverride areaOverride(String slug, Instant now) {
        return areaOverrides.findBySlug(slug).orElseGet(() -> new CuratedAreaOverride(slug, now));
    }

    private void resequencePractices(List<String> orderedSlugs, Instant now) {
        for (int position = 0; position < orderedSlugs.size(); position++) {
            CuratedPracticeOverride override = practiceOverride(orderedSlugs.get(position), now);
            override.setPosition(position, now);
            practiceOverrides.save(override);
        }
    }

    private void resequenceAreas(List<String> orderedSlugs, Instant now) {
        for (int position = 0; position < orderedSlugs.size(); position++) {
            CuratedAreaOverride override = areaOverride(orderedSlugs.get(position), now);
            override.setPosition(position, now);
            areaOverrides.save(override);
        }
    }

    private void clearPracticeDefinition(String slug) {
        practiceOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.clearDefinition(clock.instant());
                if (override.isEmpty()) {
                    practiceOverrides.delete(override);
                } else {
                    practiceOverrides.save(override);
                }
            });
    }

    private void clearAreaDefinition(String slug) {
        areaOverrides
            .findBySlug(slug)
            .ifPresent(override -> {
                override.clearDefinition(clock.instant());
                if (override.isEmpty()) {
                    areaOverrides.delete(override);
                } else {
                    areaOverrides.save(override);
                }
            });
    }
}
