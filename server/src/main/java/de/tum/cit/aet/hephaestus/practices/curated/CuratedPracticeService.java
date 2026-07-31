package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.DataIntegrityViolationConstraints;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.curated.dto.CreateCuratedPracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.curated.dto.UpdateCuratedPracticeRequestDTO;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnServerRole
@WorkspaceAgnostic("Instance-managed curated practice catalog is global")
public class CuratedPracticeService {

    private final CuratedPracticeRepository practiceRepository;
    private final CuratedPracticeRevisionRepository revisionRepository;
    private final CuratedPracticeAreaRepository areaRepository;
    private final CuratedCatalogSyncStateRepository syncStateRepository;
    private final ConfigAuditPort configAudit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CuratedPractice> list(boolean includeRetired) {
        return practiceRepository.findCatalog(includeRetired);
    }

    @Transactional(readOnly = true)
    public CuratedPractice get(String slug, boolean includeRetired) {
        CuratedPractice practice = practiceRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("CuratedPractice", slug));
        if (!includeRetired && !isEffectivelyAvailable(practice)) {
            throw new EntityNotFoundException("CuratedPractice", slug);
        }
        return practice;
    }

    @Transactional(readOnly = true)
    public List<CuratedPracticeArea> listAreas() {
        return areaRepository.findAllByOrderByDisplayOrderAscNameAsc();
    }

    @Transactional
    public CuratedPractice create(CreateCuratedPracticeRequestDTO request) {
        lockCatalogSync();
        if (practiceRepository.existsBySlug(request.slug())) {
            throw new CuratedPracticeConflictException(
                "A curated practice with slug '" + request.slug() + "' already exists."
            );
        }
        PracticeDefinition definition = definition(request);
        validate(definition);

        var now = clock.instant();
        CuratedPractice practice = new CuratedPractice();
        practice.initializeInstance(request.slug(), now);
        try {
            practice = practiceRepository.saveAndFlush(practice);
        } catch (DataIntegrityViolationException exception) {
            if (!DataIntegrityViolationConstraints.hasName(exception, "uk_curated_practice_slug")) {
                throw exception;
            }
            throw new CuratedPracticeConflictException(
                "A curated practice with slug '" + request.slug() + "' already exists.",
                exception
            );
        }
        CuratedPracticeRevision revision = appendRevision(
            practice,
            definition,
            CuratedPracticeRevisionOrigin.ADMIN,
            null,
            now
        );
        practice.revise(revision, now);
        practice = practiceRepository.saveAndFlush(practice);
        configAudit.record(
            ConfigAuditEntry.instanceCreated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
        return practice;
    }

    @Transactional
    public CuratedPractice update(
        String slug,
        CuratedPracticeVersionPrecondition precondition,
        UpdateCuratedPracticeRequestDTO request
    ) {
        lockCatalogSync();
        CuratedPractice practice = lock(slug, precondition);
        PracticeDefinition beforeDefinition = PracticeDefinition.from(practice.getCurrentRevision());
        PracticeDefinition afterDefinition = definition(request);
        validate(afterDefinition);
        if (beforeDefinition.equals(afterDefinition)) {
            return practice;
        }

        CuratedPracticeAuditSnapshot before = CuratedPracticeAuditSnapshot.from(practice);
        var now = clock.instant();
        CuratedPracticeRevision revision = appendRevision(
            practice,
            afterDefinition,
            CuratedPracticeRevisionOrigin.ADMIN,
            null,
            now
        );
        practice.overrideWith(revision, now);
        practice = practiceRepository.saveAndFlush(practice);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                before,
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
        return practice;
    }

    @Transactional
    public CuratedPractice setStatus(
        String slug,
        CuratedPracticeVersionPrecondition precondition,
        CuratedPracticeStatus status
    ) {
        lockCatalogSync();
        CuratedPractice practice = lock(slug, precondition);
        if (practice.getStatus() == status) {
            return practice;
        }
        CuratedPracticeAuditSnapshot before = CuratedPracticeAuditSnapshot.from(practice);
        var now = clock.instant();
        practice.setStatus(status, now);
        practice = practiceRepository.saveAndFlush(practice);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                before,
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
        return practice;
    }

    @Transactional
    public CuratedPractice resetOverride(String slug, CuratedPracticeVersionPrecondition precondition) {
        lockCatalogSync();
        CuratedPractice practice = lock(slug, precondition);
        if (
            practice.getSourceKind() != CuratedPracticeSourceKind.BUNDLED ||
            practice.getSyncStatus() == CuratedPracticeSyncStatus.SOURCE_REMOVED ||
            practice.getLatestBundledRevision() == null
        ) {
            throw new CuratedPracticeConflictException("No bundled definition is available for '" + slug + "'.");
        }
        if (practice.getSyncStatus() == CuratedPracticeSyncStatus.SYNCED) {
            return practice;
        }
        CuratedPracticeAuditSnapshot before = CuratedPracticeAuditSnapshot.from(practice);
        PracticeDefinition definition = PracticeDefinition.from(practice.getLatestBundledRevision());
        var now = clock.instant();
        CuratedPracticeRevision resolution = appendRevision(
            practice,
            definition,
            CuratedPracticeRevisionOrigin.ADMIN_RESOLUTION,
            null,
            now
        );
        practice.resolveToBundled(resolution, now);
        practice = practiceRepository.saveAndFlush(practice);
        configAudit.record(
            ConfigAuditEntry.instanceUpdated(
                ConfigAuditEntityType.CURATED_PRACTICE,
                practice.getId(),
                before,
                CuratedPracticeAuditSnapshot.from(practice)
            )
        );
        return practice;
    }

    private CuratedPractice lock(String slug, CuratedPracticeVersionPrecondition precondition) {
        CuratedPractice practice = practiceRepository
            .findBySlugForUpdate(slug)
            .orElseThrow(() -> new EntityNotFoundException("CuratedPractice", slug));
        if (!precondition.matches(practice.getVersion())) {
            throw new StaleCuratedPracticeException(slug);
        }
        return practice;
    }

    private void lockCatalogSync() {
        syncStateRepository
            .findBySourceForUpdate(BundledCuratedCatalogReconciler.SOURCE)
            .orElseThrow(() -> new IllegalStateException("bundled curated catalog sync state is missing"));
    }

    private void validate(PracticeDefinition definition) {
        if (definition.areaSlug() != null && !areaRepository.existsBySlug(definition.areaSlug())) {
            throw new EntityNotFoundException("CuratedPracticeArea", definition.areaSlug());
        }
        PracticeDefinitionValidator.validate(
            definition.artifactType(),
            definition.triggerEvents(),
            definition.whyItMatters(),
            definition.whatGoodLooksLike()
        );
    }

    private static PracticeDefinition definition(CreateCuratedPracticeRequestDTO request) {
        return new PracticeDefinition(
            request.name(),
            request.artifactType(),
            request.triggerEvents(),
            request.criteria(),
            request.precomputeScript(),
            request.whyItMatters(),
            request.whatGoodLooksLike(),
            request.areaSlug()
        );
    }

    private static PracticeDefinition definition(UpdateCuratedPracticeRequestDTO request) {
        return new PracticeDefinition(
            request.name(),
            request.artifactType(),
            request.triggerEvents(),
            request.criteria(),
            request.precomputeScript(),
            request.whyItMatters(),
            request.whatGoodLooksLike(),
            request.areaSlug()
        );
    }

    private CuratedPracticeRevision appendRevision(
        CuratedPractice practice,
        PracticeDefinition definition,
        CuratedPracticeRevisionOrigin origin,
        Long bundleRevision,
        java.time.Instant now
    ) {
        int revisionNumber = revisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practice.getId())
            .map(revision -> revision.getRevisionNumber() + 1)
            .orElse(1);
        return revisionRepository.save(
            new CuratedPracticeRevision(
                practice,
                revisionNumber,
                definition,
                definition.detectionFingerprint(practice.getSlug()),
                origin,
                bundleRevision,
                definition.digest(practice.getSlug()),
                now
            )
        );
    }

    private static boolean isEffectivelyAvailable(CuratedPractice practice) {
        if (practice.getRetiredAt() != null) {
            return false;
        }
        return (
            practice.getSyncStatus() != CuratedPracticeSyncStatus.SOURCE_REMOVED ||
            practice.getCurrentRevision().getOrigin() == CuratedPracticeRevisionOrigin.ADMIN
        );
    }
}
