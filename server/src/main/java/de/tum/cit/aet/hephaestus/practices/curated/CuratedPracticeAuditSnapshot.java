package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.jspecify.annotations.Nullable;

record CuratedPracticeAuditSnapshot(
    String slug,
    String name,
    WorkArtifact artifactType,
    List<String> triggerEvents,
    int revisionNumber,
    String detectionFingerprint,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug,
    CuratedPracticeStatus status,
    CuratedPracticeSourceKind sourceKind,
    CuratedPracticeSyncStatus syncStatus,
    @Nullable Long latestBundledCatalogRevision
) implements ConfigAuditSnapshot {
    static CuratedPracticeAuditSnapshot from(CuratedPractice practice) {
        CuratedPracticeRevision revision = practice.getCurrentRevision();
        return new CuratedPracticeAuditSnapshot(
            practice.getSlug(),
            revision.getName(),
            revision.getArtifactType(),
            TriggerEventsConverter.toList(revision.getTriggerEvents()).stream().sorted().toList(),
            revision.getRevisionNumber(),
            revision.getDetectionFingerprint(),
            revision.getWhyItMatters(),
            revision.getWhatGoodLooksLike(),
            revision.getAreaSlug(),
            practice.getStatus(),
            practice.getSourceKind(),
            practice.getSyncStatus(),
            practice.getLatestBundledRevision() == null ? null : practice.getLatestBundledRevision().getBundleRevision()
        );
    }
}
