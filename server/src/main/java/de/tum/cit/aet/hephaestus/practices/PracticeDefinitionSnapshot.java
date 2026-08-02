package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.jspecify.annotations.Nullable;

record PracticeDefinitionSnapshot(
    String slug,
    String name,
    WorkArtifact artifactType,
    List<String> triggerEvents,
    @Nullable Integer criteriaRevision,
    String criteriaSha256,
    @Nullable String precomputeScriptSha256,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) implements ConfigAuditSnapshot {
    static PracticeDefinitionSnapshot of(Practice practice, @Nullable Integer criteriaRevision) {
        return new PracticeDefinitionSnapshot(
            practice.getSlug(),
            practice.getName(),
            practice.getArtifactType(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()).stream().sorted().toList(),
            criteriaRevision,
            CanonicalDigest.sha256Hex(practice.getCriteria()),
            practice.getPrecomputeScript() == null ? null : CanonicalDigest.sha256Hex(practice.getPrecomputeScript()),
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            practice.getArea() == null ? null : practice.getArea().getSlug()
        );
    }
}
