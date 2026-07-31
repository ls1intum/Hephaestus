package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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
        rejectDetectorVocabulary("whyItMatters", whyItMatters);
        rejectDetectorVocabulary("whatGoodLooksLike", whatGoodLooksLike);
    }

    private static void validateTriggers(WorkArtifact artifactType, List<String> triggerEvents) {
        Set<String> allowed = TriggerEventCatalog.eligibleFor(artifactType);
        if (artifactType != WorkArtifact.CONVERSATION_THREAD && triggerEvents.isEmpty()) {
            throw new IllegalArgumentException("At least one trigger event is required for " + artifactType);
        }
        List<String> incompatible = triggerEvents
            .stream()
            .filter(event -> !allowed.contains(event))
            .toList();
        if (!incompatible.isEmpty()) {
            throw new IllegalArgumentException(
                "Trigger events " + incompatible + " are not valid for a " + artifactType
            );
        }
    }

    private static void rejectDetectorVocabulary(String field, @Nullable String value) {
        if (value != null && DETECTOR_VOCAB.matcher(value).find()) {
            throw new IllegalArgumentException(
                field + " is learner-facing and must not contain detector assessment vocabulary"
            );
        }
    }
}
