package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record PracticeDefinition(
    String name,
    ArtifactKind artifactKind,
    List<String> triggerEvents,
    String criteria,
    @Nullable String precomputeScript,
    PracticeAutomatedReviewPolicy automatedReviewPolicy,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) implements CatalogDefinition {
    public static final int MAX_PRECOMPUTE_SCRIPT_LENGTH = 100_000;

    public PracticeDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(artifactKind, "artifactKind");
        triggerEvents = List.copyOf(Objects.requireNonNull(triggerEvents, "triggerEvents").stream().sorted().toList());
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(automatedReviewPolicy, "automatedReviewPolicy");
        precomputeScript = blankToNull(precomputeScript);
        whyItMatters = blankToNull(whyItMatters);
        whatGoodLooksLike = blankToNull(whatGoodLooksLike);
    }

    public static PracticeDefinition from(Practice practice) {
        return new PracticeDefinition(
            practice.getName(),
            practice.getArtifactKind(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()),
            practice.getCriteria(),
            practice.getPrecomputeScript(),
            practice.getAutomatedReviewPolicy(),
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
            artifactKind,
            triggerEvents,
            criteria,
            precomputeScript,
            automatedReviewPolicy,
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
