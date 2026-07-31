package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevision;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record PracticeDefinition(
    String name,
    WorkArtifact artifactType,
    List<String> triggerEvents,
    String criteria,
    @Nullable String precomputeScript,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) {
    public PracticeDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(artifactType, "artifactType");
        triggerEvents = List.copyOf(Objects.requireNonNull(triggerEvents, "triggerEvents").stream().sorted().toList());
        Objects.requireNonNull(criteria, "criteria");
        precomputeScript = blankToNull(precomputeScript);
        whyItMatters = blankToNull(whyItMatters);
        whatGoodLooksLike = blankToNull(whatGoodLooksLike);
    }

    public static PracticeDefinition from(Practice practice) {
        return new PracticeDefinition(
            practice.getName(),
            practice.getArtifactType(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()),
            practice.getCriteria(),
            practice.getPrecomputeScript(),
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            practice.getArea() == null ? null : practice.getArea().getSlug()
        );
    }

    public static PracticeDefinition from(CuratedPracticeRevision revision) {
        return new PracticeDefinition(
            revision.getName(),
            revision.getArtifactType(),
            TriggerEventsConverter.toList(revision.getTriggerEvents()),
            revision.getCriteria(),
            revision.getPrecomputeScript(),
            revision.getWhyItMatters(),
            revision.getWhatGoodLooksLike(),
            revision.getAreaSlug()
        );
    }

    public JsonNode triggerEventsJson() {
        return TriggerEventsConverter.toJsonNode(triggerEvents);
    }

    public String detectionFingerprint(String slug) {
        return PracticeDetectionFingerprint.of(
            slug,
            name,
            artifactType,
            triggerEvents,
            criteria,
            precomputeScript,
            areaSlug
        );
    }

    public String digest(String slug) {
        return PracticeDefinitionDigest.digest(slug, this);
    }

    public boolean hasSameDetectorInputs(PracticeDefinition other) {
        return (
            name.equals(other.name) &&
            artifactType == other.artifactType &&
            triggerEvents.equals(other.triggerEvents) &&
            criteria.equals(other.criteria) &&
            Objects.equals(precomputeScript, other.precomputeScript) &&
            Objects.equals(areaSlug, other.areaSlug)
        );
    }

    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
