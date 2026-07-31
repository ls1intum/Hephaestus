package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnServerRole
@WorkspaceAgnostic("Bundled curated practice reconciliation is global")
public class BundledCuratedCatalogReconciler {

    static final String SOURCE = "BUNDLED";

    private final CuratedCatalogSyncStateRepository syncStateRepository;
    private final CuratedPracticeRepository practiceRepository;
    private final CuratedPracticeRevisionRepository revisionRepository;
    private final CuratedPracticeAreaRepository areaRepository;
    private final LegacyPracticeCatalogProvenanceLinker provenanceLinker;
    private final ConfigAuditPort configAudit;
    private final Clock clock;

    @Transactional
    public void reconcile(BundledPracticeCatalog catalog) {
        CuratedCatalogSyncState state = syncStateRepository
            .findBySourceForUpdate(SOURCE)
            .orElseThrow(() -> new IllegalStateException("bundled curated catalog sync state is missing"));
        Workspace legacyWorkspace =
            state.getProvenanceBackfillVersion() == 0 ? provenanceLinker.lockFirstWorkspace().orElse(null) : null;
        if (catalog.catalogRevision() < state.getCatalogRevision()) {
            log.info(
                "Skipping older bundled practice catalog: bundledRevision={}, synchronizedRevision={}",
                catalog.catalogRevision(),
                state.getCatalogRevision()
            );
        } else if (catalog.catalogRevision() == state.getCatalogRevision()) {
            if (!catalog.contentDigest().equals(state.getContentDigest())) {
                throw new IllegalStateException(
                    "bundled practice catalog revision " + catalog.catalogRevision() + " has conflicting content"
                );
            }
        } else {
            synchronize(catalog, state);
        }

        if (state.getProvenanceBackfillVersion() == 0) {
            int linked = legacyWorkspace == null ? 0 : provenanceLinker.link(legacyWorkspace);
            state.markProvenanceBackfilled(clock.instant());
            log.info("Linked {} legacy workspace practices to the curated catalog", linked);
        }
    }

    private void synchronize(BundledPracticeCatalog catalog, CuratedCatalogSyncState state) {
        Instant now = clock.instant();
        synchronizeAreas(catalog);
        Map<String, CuratedPractice> bundledBySlug = new HashMap<>();
        practiceRepository
            .findAllBundledForUpdate()
            .forEach(practice -> bundledBySlug.put(practice.getSlug(), practice));
        Set<String> incomingSlugs = new HashSet<>();
        catalog
            .practices()
            .stream()
            .sorted(java.util.Comparator.comparing(BundledPracticeCatalog.BundledPractice::slug))
            .forEach(incoming -> {
                incomingSlugs.add(incoming.slug());
                CuratedPractice practice = bundledBySlug.get(incoming.slug());
                if (practice == null) {
                    practiceRepository
                        .findBySlugForUpdate(incoming.slug())
                        .ifPresentOrElse(
                            ignored ->
                                log.warn(
                                    "Bundled practice slug is owned by an instance practice: slug={}",
                                    incoming.slug()
                                ),
                            () -> createBundled(incoming, catalog.catalogRevision(), now)
                        );
                } else {
                    reconcilePractice(practice, incoming, catalog.catalogRevision(), now);
                }
            });
        bundledBySlug
            .values()
            .stream()
            .filter(practice -> !incomingSlugs.contains(practice.getSlug()))
            .filter(practice -> practice.getSyncStatus() != CuratedPracticeSyncStatus.SOURCE_REMOVED)
            .forEach(practice -> {
                CuratedPracticeAuditSnapshot before = CuratedPracticeAuditSnapshot.from(practice);
                practice.markSourceRemoved(now);
                practiceRepository.save(practice);
                recordUpdate(practice, before);
            });
        state.synchronizedTo(catalog.catalogRevision(), catalog.contentDigest(), now);
        log.info(
            "Synchronized bundled practice catalog: revision={}, practices={}",
            catalog.catalogRevision(),
            catalog.practices().size()
        );
    }

    private void synchronizeAreas(BundledPracticeCatalog catalog) {
        for (BundledPracticeCatalog.BundledArea incoming : catalog.areas()) {
            CuratedPracticeArea area = areaRepository.findBySlug(incoming.slug()).orElseGet(CuratedPracticeArea::new);
            area.setSlug(incoming.slug());
            area.setName(incoming.name());
            area.setDescription(incoming.description());
            area.setDisplayOrder(incoming.displayOrder());
            area.setIcon(incoming.icon());
            area.setColor(incoming.color());
            areaRepository.save(area);
        }
    }

    private void createBundled(BundledPracticeCatalog.BundledPractice incoming, long bundleRevision, Instant now) {
        CuratedPractice practice = new CuratedPractice();
        practice.initializeBundled(incoming.slug(), now);
        practice = practiceRepository.saveAndFlush(practice);
        CuratedPracticeRevision revision = appendBundled(practice, incoming, bundleRevision, now);
        practice.applyBundled(revision, now);
        practice = practiceRepository.saveAndFlush(practice);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
    }

    private void reconcilePractice(
        CuratedPractice practice,
        BundledPracticeCatalog.BundledPractice incoming,
        long bundleRevision,
        Instant now
    ) {
        CuratedPracticeAuditSnapshot before = CuratedPracticeAuditSnapshot.from(practice);
        CuratedPracticeRevision latestBundled = practice.getLatestBundledRevision();
        PracticeDefinition incomingDefinition = incoming.definition();
        if (PracticeDefinition.from(latestBundled).equals(incomingDefinition)) {
            if (practice.getSyncStatus() == CuratedPracticeSyncStatus.SOURCE_REMOVED) {
                if (practice.getCurrentRevision().getOrigin() == CuratedPracticeRevisionOrigin.ADMIN) {
                    practice.holdBundledUpdate(latestBundled, now);
                } else {
                    practice.reconcileUnchanged(now);
                }
            }
        } else {
            CuratedPracticeRevision bundledRevision = appendBundled(practice, incoming, bundleRevision, now);
            if (PracticeDefinition.from(practice.getCurrentRevision()).equals(incomingDefinition)) {
                practice.acceptMatchingBundled(bundledRevision, now);
            } else if (
                practice.getSyncStatus() == CuratedPracticeSyncStatus.SYNCED ||
                (practice.getSyncStatus() == CuratedPracticeSyncStatus.SOURCE_REMOVED &&
                    practice.getCurrentRevision().getOrigin() != CuratedPracticeRevisionOrigin.ADMIN)
            ) {
                practice.applyBundled(bundledRevision, now);
            } else {
                practice.holdBundledUpdate(bundledRevision, now);
            }
        }
        CuratedPracticeAuditSnapshot after = CuratedPracticeAuditSnapshot.from(practice);
        if (!before.equals(after)) {
            practiceRepository.save(practice);
            recordUpdate(practice, before);
        }
    }

    private CuratedPracticeRevision appendBundled(
        CuratedPractice practice,
        BundledPracticeCatalog.BundledPractice incoming,
        long bundleRevision,
        Instant now
    ) {
        return revisionRepository.save(
            new CuratedPracticeRevision(
                practice,
                nextRevisionNumber(practice),
                incoming.definition(),
                incoming.definition().detectionFingerprint(practice.getSlug()),
                CuratedPracticeRevisionOrigin.BUNDLED,
                bundleRevision,
                incoming.definitionDigest(),
                now
            )
        );
    }

    private int nextRevisionNumber(CuratedPractice practice) {
        return revisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(revision -> revision.getRevisionNumber() + 1)
            .orElse(1);
    }

    private void recordUpdate(CuratedPractice practice, CuratedPracticeAuditSnapshot before) {
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                before,
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
    }
}
