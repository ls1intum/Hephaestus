package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
            sha256(practice.getCriteria()),
            practice.getPrecomputeScript() == null ? null : sha256(practice.getPrecomputeScript()),
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            practice.getArea() == null ? null : practice.getArea().getSlug()
        );
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
