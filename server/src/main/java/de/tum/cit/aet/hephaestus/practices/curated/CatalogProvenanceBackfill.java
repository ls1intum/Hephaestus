package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeCatalogInstallation;
import de.tum.cit.aet.hephaestus.practices.PracticeCatalogInstallationRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Matches the practices and areas of workspaces seeded before the catalog existed back to the catalog
 * entries they came from.
 *
 * <p>Two things left those copies unlinked. The schema change could write a full revision row for
 * every practice but could not fingerprint it, because the fingerprint is defined in Java; and
 * nothing recorded which catalog entry a copy came from. This fills in both, once per workspace — a
 * workspace seeded by the current code is stamped as it is created and never reaches here.
 *
 * <p>Only copies that still match are linked. A workspace that edited its copy keeps no claim to the
 * catalog, which is the point: an unlinked practice is one this instance can no longer call the
 * catalog's.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnServerRole
@WorkspaceAgnostic("Provenance is stamped for every workspace in turn")
public class CatalogProvenanceBackfill {

    private final PracticeCatalogInstallationRepository installationRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final PracticeRevisionRepository revisionRepository;
    private final CuratedCatalogService catalogService;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /** @return how many practices and areas were stamped across every workspace still awaiting it */
    public Stamped run() {
        List<Long> pending = installationRepository.findWorkspaceIdsAwaitingProvenanceLink();
        if (pending.isEmpty()) {
            return new Stamped(0, 0);
        }
        EffectiveCatalog catalog = catalogService.catalog();
        Stamped total = new Stamped(0, 0);
        for (Long workspaceId : pending) {
            try {
                // Each workspace commits on its own: one workspace's data cannot block the rest, and a
                // workspace that fails keeps its marker unset so the next boot tries again.
                total = total.plus(transactionOperations.execute(ignored -> stamp(workspaceId, catalog)));
            } catch (RuntimeException exception) {
                log.error("Could not stamp catalog provenance: workspaceId={}", workspaceId, exception);
            }
        }
        log.info(
            "Stamped catalog provenance for {} workspace(s): {} practices, {} areas",
            pending.size(),
            total.practices(),
            total.areas()
        );
        return total;
    }

    public record Stamped(int practices, int areas) {
        Stamped plus(Stamped other) {
            return other == null ? this : new Stamped(practices + other.practices(), areas + other.areas());
        }
    }

    private Stamped stamp(Long workspaceId, EffectiveCatalog catalog) {
        PracticeCatalogInstallation installation = installationRepository
            .findByWorkspaceIdForUpdate(workspaceId)
            .orElse(null);
        if (installation == null || installation.getProvenanceLinkedAt() != null) {
            return new Stamped(0, 0);
        }
        fingerprintMigratedRevisions(workspaceId);
        int areas = stampAreas(workspaceId, catalog);
        int practices = stampPractices(workspaceId, catalog);
        installation.markProvenanceLinked(clock.instant());
        installationRepository.save(installation);
        return new Stamped(practices, areas);
    }

    private void fingerprintMigratedRevisions(Long workspaceId) {
        for (PracticeRevision revision : revisionRepository.findDefinitionRevisionsMissingFingerprint(workspaceId)) {
            revisionRepository.setDetectionFingerprint(revision.getId(), revision.recomputeDetectionFingerprint());
        }
    }

    private int stampPractices(Long workspaceId, EffectiveCatalog catalog) {
        int stamped = 0;
        for (Practice practice : practiceRepository.findByFilters(workspaceId, null)) {
            if (practice.getSourceCuratedSlug() != null || practice.getCurrentRevision() == null) {
                continue;
            }
            String fingerprint = PracticeDefinition.from(practice).detectionFingerprint(practice.getSlug());
            boolean matchesCatalog = catalog
                .practice(practice.getSlug())
                .map(entry -> entry.effective().detectionFingerprint(entry.slug()).equals(fingerprint))
                .orElse(false);
            if (!matchesCatalog) {
                continue;
            }
            practice.setSourceCuratedSlug(practice.getSlug());
            practice.setSourceCuratedFingerprint(fingerprint);
            practiceRepository.save(practice);
            stamped++;
        }
        return stamped;
    }

    private int stampAreas(Long workspaceId, EffectiveCatalog catalog) {
        int stamped = 0;
        for (PracticeArea area : practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)) {
            if (area.getSourceCuratedSlug() != null) {
                continue;
            }
            String fingerprint = AreaDefinition.from(area).detectionFingerprint(area.getSlug());
            boolean matchesCatalog = catalog
                .area(area.getSlug())
                .map(entry -> entry.effective().detectionFingerprint(entry.slug()).equals(fingerprint))
                .orElse(false);
            if (!matchesCatalog) {
                continue;
            }
            area.setSourceCuratedSlug(area.getSlug());
            area.setSourceCuratedFingerprint(fingerprint);
            practiceAreaRepository.save(area);
            stamped++;
        }
        return stamped;
    }
}
