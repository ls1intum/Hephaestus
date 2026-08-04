package de.tum.cit.aet.hephaestus.practices;

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
    PracticeAutomatedAssessmentPolicy automatedAssessmentPolicy,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) implements CatalogDefinition {
    public static final int MAX_PRECOMPUTE_SCRIPT_LENGTH = 100_000;

    public PracticeDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(artifactType, "artifactType");
        triggerEvents = List.copyOf(Objects.requireNonNull(triggerEvents, "triggerEvents").stream().sorted().toList());
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(automatedAssessmentPolicy, "automatedAssessmentPolicy");
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
            practice.getAutomatedAssessmentPolicy(),
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            practice.getArea() == null ? null : practice.getArea().getSlug()
        );
    }

    public JsonNode triggerEventsJson() {
        return TriggerEventsConverter.toJsonNode(triggerEvents);
    }

    @Override
    public String provenanceFingerprint(String slug) {
        return ReviewRuleFingerprint.of(
            slug,
            name,
            artifactType,
            triggerEvents,
            criteria,
            precomputeScript,
            automatedAssessmentPolicy,
            areaSlug
        );
    }

    @Override
    public String digest(String slug) {
        return PracticeDefinitionDigest.digest(slug, this);
    }

    public String exactFingerprint(String slug) {
        return "v1:" + digest(slug);
    }

    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
