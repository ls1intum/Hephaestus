package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog.BundledEntry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The instance catalog: reading what it currently offers, and recording what an administrator says
 * about it.
 *
 * <p>There is no synchronization step and nothing to keep in step. Reading composes the shipped
 * catalog with the override rows; writing stores or removes one row. An entry an administrator has
 * not touched follows Hephaestus because nothing was written about it, not because anything ran.
 */
@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("The instance catalog is global")
// Deliberately not @ConditionalOnServerRole: this reads a classpath file and two global tables, and
// the workspace-facing surfaces that report catalog provenance are themselves not role-gated.
public class CuratedCatalogService {

    private final BundledPracticeCatalogLoader loader;
    private final CuratedPracticeOverrideRepository practiceOverrides;
    private final CuratedAreaOverrideRepository areaOverrides;
    private final ConfigAuditPort configAudit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EffectiveCatalog catalog() {
        BundledPracticeCatalog bundled = loader.catalog();
        return new EffectiveCatalog(
            compose(
                bundled.areas(),
                areaOverrides.findAll(),
                CuratedAreaOverride::getSlug,
                CuratedAreaOverride::definition,
                CuratedAreaOverride::getBasedOnDigest,
                CuratedAreaOverride::getRetiredAt,
                CuratedAreaOverride::getVersion,
                CuratedAreaOverride::getUpdatedAt,
                Comparator.comparingInt((CatalogEntry<AreaDefinition> entry) ->
                    entry.effective().displayOrder()
                ).thenComparing(entry -> entry.effective().name())
            ),
            compose(
                bundled.practices(),
                practiceOverrides.findAll(),
                CuratedPracticeOverride::getSlug,
                CuratedPracticeOverride::definition,
                CuratedPracticeOverride::getBasedOnDigest,
                CuratedPracticeOverride::getRetiredAt,
                CuratedPracticeOverride::getVersion,
                CuratedPracticeOverride::getUpdatedAt,
                Comparator.comparing(
                    (CatalogEntry<PracticeDefinition> entry) -> entry.effective().areaSlug(),
                    Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(entry -> entry.effective().name())
            )
        );
    }

    @Transactional(readOnly = true)
    public CatalogEntry<PracticeDefinition> practice(String slug) {
        return catalog()
            .practice(slug)
            .orElseThrow(() -> new EntityNotFoundException("CuratedPractice", slug));
    }

    @Transactional(readOnly = true)
    public CatalogEntry<AreaDefinition> area(String slug) {
        return catalog()
            .area(slug)
            .orElseThrow(() -> new EntityNotFoundException("CuratedPracticeArea", slug));
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> writePractice(
        String slug,
        @Nullable CuratedVersionPrecondition precondition,
        PracticeDefinition definition
    ) {
        EffectiveCatalog before = catalog();
        validate(before, definition);
        CatalogEntry<PracticeDefinition> entry = require(before.practice(slug), "CuratedPractice", slug, precondition);
        if (entry.effective().equals(definition)) {
            return entry;
        }
        CuratedPracticeOverride override = practiceOverrides
            .findBySlugForUpdate(slug)
            .orElseGet(() -> new CuratedPracticeOverride(slug, clock.instant()));
        override.write(definition, digestOf(entry.shipped(), slug), clock.instant());
        practiceOverrides.save(override);
        return recordPractice(slug, entry);
    }

    /** Adds a practice this instance authors. The slug must be free in the offered catalog. */
    @Transactional
    public CatalogEntry<PracticeDefinition> createPractice(String slug, PracticeDefinition definition) {
        EffectiveCatalog before = catalog();
        validate(before, definition);
        if (before.practice(slug).isPresent()) {
            throw new CuratedCatalogConflictException("A practice with slug '" + slug + "' already exists.");
        }
        CuratedPracticeOverride override = new CuratedPracticeOverride(slug, clock.instant());
        override.write(definition, null, clock.instant());
        practiceOverrides.save(override);
        CatalogEntry<PracticeDefinition> created = practice(slug);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                created.slug(),
                CatalogEntrySnapshot.of(created)
            )
        );
        return created;
    }

    /** Discards this instance's definition, so the entry follows Hephaestus again. */
    @Transactional
    public CatalogEntry<PracticeDefinition> resetPractice(
        String slug,
        @Nullable CuratedVersionPrecondition precondition
    ) {
        CatalogEntry<PracticeDefinition> entry = require(
            catalog().practice(slug),
            "CuratedPractice",
            slug,
            precondition
        );
        if (entry.shipped() == null) {
            throw new CuratedCatalogConflictException("Hephaestus ships no definition for '" + slug + "'.");
        }
        practiceOverrides
            .findBySlugForUpdate(slug)
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

    /** Records that the administrator has seen what ships now and is keeping their own definition. */
    @Transactional
    public CatalogEntry<PracticeDefinition> keepPractice(
        String slug,
        @Nullable CuratedVersionPrecondition precondition
    ) {
        CatalogEntry<PracticeDefinition> entry = require(
            catalog().practice(slug),
            "CuratedPractice",
            slug,
            precondition
        );
        practiceOverrides
            .findBySlugForUpdate(slug)
            .ifPresent(override -> {
                override.acknowledge(digestOf(entry.shipped(), slug), clock.instant());
                practiceOverrides.save(override);
            });
        return recordPractice(slug, entry);
    }

    @Transactional
    public CatalogEntry<PracticeDefinition> setPracticeStatus(
        String slug,
        @Nullable CuratedVersionPrecondition precondition,
        CuratedStatus status
    ) {
        CatalogEntry<PracticeDefinition> entry = require(
            catalog().practice(slug),
            "CuratedPractice",
            slug,
            precondition
        );
        if (entry.retired() == (status == CuratedStatus.RETIRED)) {
            return entry;
        }
        CuratedPracticeOverride override = practiceOverrides
            .findBySlugForUpdate(slug)
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
        @Nullable CuratedVersionPrecondition precondition,
        AreaDefinition definition
    ) {
        CatalogEntry<AreaDefinition> entry = require(catalog().area(slug), "CuratedPracticeArea", slug, precondition);
        if (entry.effective().equals(definition)) {
            return entry;
        }
        CuratedAreaOverride override = areaOverrides
            .findBySlugForUpdate(slug)
            .orElseGet(() -> new CuratedAreaOverride(slug, clock.instant()));
        override.write(definition, digestOf(entry.shipped(), slug), clock.instant());
        areaOverrides.save(override);
        return recordArea(slug, entry);
    }

    @Transactional
    public CatalogEntry<AreaDefinition> createArea(String slug, AreaDefinition definition) {
        if (catalog().area(slug).isPresent()) {
            throw new CuratedCatalogConflictException("An area with slug '" + slug + "' already exists.");
        }
        CuratedAreaOverride override = new CuratedAreaOverride(slug, clock.instant());
        override.write(definition, null, clock.instant());
        areaOverrides.save(override);
        CatalogEntry<AreaDefinition> created = area(slug);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE_AREA,
                created.slug(),
                CatalogEntrySnapshot.of(created)
            )
        );
        return created;
    }

    @Transactional
    public CatalogEntry<AreaDefinition> resetArea(String slug, @Nullable CuratedVersionPrecondition precondition) {
        CatalogEntry<AreaDefinition> entry = require(catalog().area(slug), "CuratedPracticeArea", slug, precondition);
        if (entry.shipped() == null) {
            throw new CuratedCatalogConflictException("Hephaestus ships no definition for '" + slug + "'.");
        }
        areaOverrides
            .findBySlugForUpdate(slug)
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
    public CatalogEntry<AreaDefinition> keepArea(String slug, @Nullable CuratedVersionPrecondition precondition) {
        CatalogEntry<AreaDefinition> entry = require(catalog().area(slug), "CuratedPracticeArea", slug, precondition);
        areaOverrides
            .findBySlugForUpdate(slug)
            .ifPresent(override -> {
                override.acknowledge(digestOf(entry.shipped(), slug), clock.instant());
                areaOverrides.save(override);
            });
        return recordArea(slug, entry);
    }

    @Transactional
    public CatalogEntry<AreaDefinition> setAreaStatus(
        String slug,
        @Nullable CuratedVersionPrecondition precondition,
        CuratedStatus status
    ) {
        CatalogEntry<AreaDefinition> entry = require(catalog().area(slug), "CuratedPracticeArea", slug, precondition);
        if (entry.retired() == (status == CuratedStatus.RETIRED)) {
            return entry;
        }
        CuratedAreaOverride override = areaOverrides
            .findBySlugForUpdate(slug)
            .orElseGet(() -> new CuratedAreaOverride(slug, clock.instant()));
        override.setStatus(status, clock.instant());
        if (override.isEmpty()) {
            areaOverrides.delete(override);
        } else {
            areaOverrides.save(override);
        }
        return recordArea(slug, entry);
    }

    private CatalogEntry<PracticeDefinition> recordPractice(String slug, CatalogEntry<PracticeDefinition> before) {
        CatalogEntry<PracticeDefinition> after = practice(slug);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                slug,
                CatalogEntrySnapshot.of(before),
                CatalogEntrySnapshot.of(after)
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
                CatalogEntrySnapshot.of(before),
                CatalogEntrySnapshot.of(after)
            )
        );
        return after;
    }

    private static void validate(EffectiveCatalog catalog, PracticeDefinition definition) {
        if (definition.areaSlug() != null && catalog.area(definition.areaSlug()).isEmpty()) {
            throw new EntityNotFoundException("CuratedPracticeArea", definition.areaSlug());
        }
        PracticeDefinitionValidator.validate(
            definition.artifactType(),
            definition.triggerEvents(),
            definition.whyItMatters(),
            definition.whatGoodLooksLike()
        );
    }

    private static <D extends CatalogDefinition> CatalogEntry<D> require(
        java.util.Optional<CatalogEntry<D>> entry,
        String type,
        String slug,
        @Nullable CuratedVersionPrecondition precondition
    ) {
        CatalogEntry<D> found = entry.orElseThrow(() -> new EntityNotFoundException(type, slug));
        if (precondition == null) {
            throw new CuratedPreconditionRequiredException();
        }
        if (!precondition.matches(found.etag())) {
            throw new StaleCuratedEntryException(type, slug);
        }
        return found;
    }

    private static @Nullable String digestOf(@Nullable CatalogDefinition definition, String slug) {
        return definition == null ? null : definition.digest(slug);
    }

    /**
     * Lays the override rows over what the build ships. Slugs the build no longer carries still
     * appear when something was said about them, so an instance never loses sight of its own work.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static <D extends CatalogDefinition, O> List<CatalogEntry<D>> compose(
        List<BundledEntry<D>> shipped,
        List<O> overrides,
        Function<O, String> slugOf,
        Function<O, @Nullable D> definitionOf,
        Function<O, @Nullable String> basedOnDigestOf,
        Function<O, @Nullable Instant> retiredAtOf,
        Function<O, Long> versionOf,
        Function<O, Instant> updatedAtOf,
        Comparator<CatalogEntry<D>> order
    ) {
        Map<String, O> bySlug = overrides.stream().collect(Collectors.toMap(slugOf, Function.identity()));
        Set<String> slugs = new LinkedHashSet<>(shipped.stream().map(BundledEntry::slug).toList());
        slugs.addAll(bySlug.keySet());
        Map<String, D> shippedBySlug = shipped
            .stream()
            .collect(Collectors.toMap(BundledEntry::slug, BundledEntry::definition));

        List<CatalogEntry<D>> entries = new ArrayList<>();
        for (String slug : slugs) {
            D shippedDefinition = shippedBySlug.get(slug);
            O override = bySlug.get(slug);
            if (override == null) {
                entries.add(CatalogEntry.shippedOnly(slug, shippedDefinition));
                continue;
            }
            D overridden = definitionOf.apply(override);
            D effective = overridden != null ? overridden : shippedDefinition;
            if (effective == null) {
                // Only a retirement remains for a slug the build no longer ships; nothing to offer.
                continue;
            }
            entries.add(
                new CatalogEntry<>(
                    slug,
                    effective,
                    shippedDefinition,
                    overridden,
                    basedOnDigestOf.apply(override),
                    retiredAtOf.apply(override) != null,
                    versionOf.apply(override),
                    updatedAtOf.apply(override)
                )
            );
        }
        return entries.stream().sorted(order).toList();
    }
}
