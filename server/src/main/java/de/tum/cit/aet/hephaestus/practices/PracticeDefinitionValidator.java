package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PracticeDefinitionValidator {

    private static final Pattern DETECTOR_VOCAB = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");

    private PracticeDefinitionValidator() {}

    public static void validate(
        WorkArtifact artifactType,
        List<String> triggerEvents,
        @Nullable String whyItMatters,
        @Nullable String whatGoodLooksLike
    ) {
        validateTriggers(artifactType, triggerEvents);
        rejectDetectorVocabulary("Why it matters", whyItMatters);
        rejectDetectorVocabulary("What good looks like", whatGoodLooksLike);
    }

    private static void validateTriggers(WorkArtifact artifactType, List<String> triggerEvents) {
        if (new HashSet<>(triggerEvents).size() != triggerEvents.size()) {
            throw new IllegalArgumentException("Trigger events must not contain duplicates");
        }
        Set<String> allowed = TriggerEventCatalog.eligibleFor(artifactType);
        if (artifactType != WorkArtifact.CONVERSATION_THREAD && triggerEvents.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one event that starts a review");
        }
        List<String> incompatible = triggerEvents
            .stream()
            .filter(event -> !allowed.contains(event))
            .toList();
        if (!incompatible.isEmpty()) {
            throw new IllegalArgumentException("Choose review events available for the selected work type");
        }
    }

    private static void rejectDetectorVocabulary(String field, @Nullable String value) {
        if (value != null && DETECTOR_VOCAB.matcher(value).find()) {
            throw new IllegalArgumentException(
                field + " is guidance for people and must not use detector result labels"
            );
        }
    }
}
