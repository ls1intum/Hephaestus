package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDigest;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import org.jspecify.annotations.Nullable;

record CuratedPracticeSnapshot(
    String slug,
    CatalogEntryState state,
    boolean offered,
    int position,
    String name,
    WorkArtifact artifactType,
    List<String> triggerEvents,
    String criteriaSha256,
    @Nullable String precomputeScriptSha256,
    String evidenceDeclarationSha256,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug,
    @Nullable String shippedDigest
) implements ConfigAuditSnapshot {
    static CuratedPracticeSnapshot of(CatalogEntry<PracticeDefinition> entry) {
        PracticeDefinition definition = entry.effective();
        return new CuratedPracticeSnapshot(
            entry.slug(),
            entry.state(),
            entry.offered(),
            entry.position(),
            definition.name(),
            definition.artifactType(),
            definition.triggerEvents(),
            CanonicalDigest.sha256Hex(definition.criteria()),
            definition.precomputeScript() == null ? null : CanonicalDigest.sha256Hex(definition.precomputeScript()),
            PracticeEvidenceDigest.digest(definition.evidence()),
            definition.whyItMatters(),
            definition.whatGoodLooksLike(),
            definition.areaSlug(),
            entry.shipped() == null ? null : entry.shipped().digest(entry.slug())
        );
    }
}
