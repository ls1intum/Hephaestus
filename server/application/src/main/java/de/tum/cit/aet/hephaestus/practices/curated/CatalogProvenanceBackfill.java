package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeCatalogInstallation;
import de.tum.cit.aet.hephaestus.practices.PracticeCatalogInstallationRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionService;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
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
    private final PracticeGroupRepository practiceGroupRepository;
    private final PracticeRevisionRepository revisionRepository;
    private final PracticeRevisionService revisionService;
    private final CuratedCatalogService curatedCatalogService;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public Stamped run() {
        alignVersionedEvidence();
        fingerprintMigratedRevisions();
        List<Long> pending = installationRepository.findWorkspaceIdsAwaitingProvenanceLink();
        if (pending.isEmpty()) {
            return new Stamped(0, 0);
        }
        EffectiveCatalog catalog = curatedCatalogService.catalog();
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
            "Stamped catalog provenance for {} workspace(s): {} practices, {} groups",
            completed,
            total.practices(),
            total.groups()
        );
        return total;
    }

    private void alignVersionedEvidence() {
        EffectiveCatalog catalog = curatedCatalogService.catalog();
        for (Long practiceId : practiceRepository.findSourceAlignedV1PracticeIds()) {
            try {
                transactionOperations.executeWithoutResult(ignored -> {
                    Practice managed = practiceRepository.findById(practiceId).orElseThrow();
                    String sourceSlug = Objects.requireNonNull(managed.getSourceCuratedSlug());
                    catalog
                        .practice(sourceSlug)
                        .ifPresent(entry -> {
                            PracticeDefinition effective = entry.effective();
                            PracticeDefinition aligned = new PracticeDefinition(
                                managed.getName(),
                                managed.getBindings(),
                                managed.getCriteria(),
                                managed.getPrecomputeScript(),
                                effective.automatedReviewPolicy(),
                                managed.getWhyItMatters(),
                                managed.getWhatGoodLooksLike(),
                                managed.getGroup() == null ? null : managed.getGroup().getSlug()
                            );
                            if (
                                !aligned
                                    .provenanceFingerprint(entry.slug())
                                    .equals(effective.provenanceFingerprint(entry.slug()))
                            ) {
                                return;
                            }
                            managed.setAutomatedReviewPolicy(effective.automatedReviewPolicy());
                            managed.setSourceCuratedFingerprint(effective.provenanceFingerprint(entry.slug()));
                            revisionService.append(managed);
                        });
                });
            } catch (RuntimeException exception) {
                log.error("Could not align catalog evidence: practiceId={}", practiceId, exception);
            }
        }
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

    public record Stamped(int practices, int groups) {
        Stamped plus(Stamped other) {
            return other == null ? this : new Stamped(practices + other.practices(), groups + other.groups());
        }
    }

    private Stamped stamp(Long workspaceId, EffectiveCatalog catalog) {
        PracticeCatalogInstallation installation = installationRepository
            .findByWorkspaceIdForUpdate(workspaceId)
            .orElse(null);
        if (installation == null || installation.getProvenanceLinkedAt() != null) {
            return new Stamped(0, 0);
        }
        int groups = stampGroups(workspaceId, catalog);
        int practices = stampPractices(workspaceId, catalog);
        installation.markProvenanceLinked(clock.instant());
        installationRepository.save(installation);
        return new Stamped(practices, groups);
    }

    private void fingerprintMigratedRevisions(Long workspaceId) {
        for (PracticeRevision revision : revisionRepository.findDefinitionRevisionsMissingFingerprint(workspaceId)) {
            revisionRepository.setReviewRuleFingerprint(
                Objects.requireNonNull(revision.getId()),
                revision.computeReviewRuleFingerprint()
            );
        }
    }

    private int stampPractices(Long workspaceId, EffectiveCatalog catalog) {
        int stamped = 0;
        for (Practice practice : practiceRepository.findAllForCatalog(workspaceId)) {
            if (practice.getSourceCuratedSlug() != null || practice.getCurrentRevision() == null) {
                continue;
            }
            String fingerprint = PracticeDefinition.from(practice).provenanceFingerprint(practice.getSlug());
            boolean matchesCatalog = catalog
                .practices()
                .stream()
                .filter(entry -> entry.slug().equals(practice.getSlug()))
                .findFirst()
                .map(entry -> entry.effective().provenanceFingerprint(entry.slug()).equals(fingerprint))
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

    private int stampGroups(Long workspaceId, EffectiveCatalog catalog) {
        int stamped = 0;
        for (PracticeGroup group : practiceGroupRepository.findByWorkspaceIdOrderByDisplayOrderAscNameAsc(
            workspaceId
        )) {
            if (group.getSourceCuratedSlug() != null) {
                continue;
            }
            String fingerprint = GroupDefinition.from(group).provenanceFingerprint(group.getSlug());
            boolean matchesCatalog = catalog
                .groups()
                .stream()
                .filter(entry -> entry.slug().equals(group.getSlug()))
                .findFirst()
                .map(entry -> entry.effective().provenanceFingerprint(entry.slug()).equals(fingerprint))
                .orElse(false);
            if (!matchesCatalog) {
                continue;
            }
            group.setSourceCuratedSlug(group.getSlug());
            group.setSourceCuratedFingerprint(fingerprint);
            practiceGroupRepository.save(group);
            stamped++;
        }
        return stamped;
    }
}
