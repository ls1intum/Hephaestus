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

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnServerRole
@WorkspaceAgnostic("Repairs migrated fingerprints and links eligible catalog installations")
public class CatalogProvenanceBackfill {

    private final PracticeCatalogInstallationRepository installationRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final PracticeRevisionRepository revisionRepository;
    private final BundledPracticeCatalogLoader bundledCatalogLoader;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public Stamped run() {
        fingerprintMigratedRevisions();
        List<Long> pending = installationRepository.findWorkspaceIdsAwaitingProvenanceLink();
        if (pending.isEmpty()) {
            return new Stamped(0, 0);
        }
        BundledPracticeCatalog catalog = bundledCatalogLoader.catalog();
        Stamped total = new Stamped(0, 0);
        int completed = 0;
        for (Long workspaceId : pending) {
            try {
                total = total.plus(transactionOperations.execute(ignored -> stamp(workspaceId, catalog)));
                completed++;
            } catch (RuntimeException exception) {
                log.error("Could not stamp catalog provenance: workspaceId={}", workspaceId, exception);
            }
        }
        log.info(
            "Stamped catalog provenance for {} workspace(s): {} practices, {} areas",
            completed,
            total.practices(),
            total.areas()
        );
        return total;
    }

    private void fingerprintMigratedRevisions() {
        for (Long workspaceId : revisionRepository.findWorkspaceIdsWithDefinitionRevisionsMissingFingerprint()) {
            try {
                transactionOperations.executeWithoutResult(ignored -> fingerprintMigratedRevisions(workspaceId));
            } catch (RuntimeException exception) {
                log.error("Could not fingerprint migrated practice revisions: workspaceId={}", workspaceId, exception);
            }
        }
    }

    public record Stamped(int practices, int areas) {
        Stamped plus(Stamped other) {
            return other == null ? this : new Stamped(practices + other.practices(), areas + other.areas());
        }
    }

    private Stamped stamp(Long workspaceId, BundledPracticeCatalog catalog) {
        PracticeCatalogInstallation installation = installationRepository
            .findByWorkspaceIdForUpdate(workspaceId)
            .orElse(null);
        if (installation == null || installation.getProvenanceLinkedAt() != null) {
            return new Stamped(0, 0);
        }
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

    private int stampPractices(Long workspaceId, BundledPracticeCatalog catalog) {
        int stamped = 0;
        for (Practice practice : practiceRepository.findByFilters(workspaceId, null)) {
            if (practice.getSourceCuratedSlug() != null || practice.getCurrentRevision() == null) {
                continue;
            }
            String fingerprint = PracticeDefinition.from(practice).detectionFingerprint(practice.getSlug());
            boolean matchesCatalog = catalog
                .practices()
                .stream()
                .filter(entry -> entry.slug().equals(practice.getSlug()))
                .findFirst()
                .map(entry -> entry.definition().detectionFingerprint(entry.slug()).equals(fingerprint))
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

    private int stampAreas(Long workspaceId, BundledPracticeCatalog catalog) {
        int stamped = 0;
        for (PracticeArea area : practiceAreaRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)) {
            if (area.getSourceCuratedSlug() != null) {
                continue;
            }
            String fingerprint = AreaDefinition.from(area).detectionFingerprint(area.getSlug());
            boolean matchesCatalog = catalog
                .areas()
                .stream()
                .filter(entry -> entry.slug().equals(area.getSlug()))
                .findFirst()
                .map(entry -> entry.definition().detectionFingerprint(entry.slug()).equals(fingerprint))
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
