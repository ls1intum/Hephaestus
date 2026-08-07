package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class PracticeDefinitionValidator {

    private static final Pattern DETECTOR_VOCAB = Pattern.compile("\\b(?:PRESENT|ABSENT|GOOD|BAD|NOT_APPLICABLE)\\b");

    private final ArtifactSourceCatalogRegistry sourceCatalogs;
    private final PracticeTriggerOptions triggerOptions;

    public PracticeDefinitionValidator(
        ArtifactSourceCatalogRegistry sourceCatalogs,
        PracticeTriggerOptions triggerOptions
    ) {
        this.sourceCatalogs = sourceCatalogs;
        this.triggerOptions = triggerOptions;
    }

    public void validate(PracticeDefinition definition) {
        boolean canRunAutomatedReview = definition
            .automatedReviewPolicy()
            .automatedReview()
            .canAttemptAutomatedReview();
        validateTriggers(definition.artifactKind(), definition.triggerEvents(), canRunAutomatedReview);
        if (!canRunAutomatedReview && definition.precomputeScript() != null) {
            throw new IllegalArgumentException("A practice Hephaestus cannot review cannot define a precompute script");
        }
        rejectDetectorVocabulary("Why it matters", definition.whyItMatters());
        rejectDetectorVocabulary("What good looks like", definition.whatGoodLooksLike());
        validateEvidence(definition.artifactKind(), definition.automatedReviewPolicy());
    }

    private void validateTriggers(
        ArtifactKind artifactKind,
        List<String> triggerEvents,
        boolean canRunAutomatedReview
    ) {
        if (new HashSet<>(triggerEvents).size() != triggerEvents.size()) {
            throw new IllegalArgumentException("Trigger events must not contain duplicates");
        }
        if (!canRunAutomatedReview && !triggerEvents.isEmpty()) {
            throw new IllegalArgumentException(
                "A practice Hephaestus cannot review cannot define events that start a review"
            );
        }
        Set<String> allowed = triggerOptions.eligibleFor(artifactKind);
        if (
            canRunAutomatedReview && !ArtifactKinds.CONVERSATION_THREAD.equals(artifactKind) && triggerEvents.isEmpty()
        ) {
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

    /**
     * A practice may only read evidence that could exist for the kind of thing it reviews.
     *
     * <p>The allow-list is the sources that declare they apply to this artifact kind, asked of the
     * catalog each time rather than stored as a named profile. What each source demands of its capture
     * is no longer checked here at all: it is stated once in the source contract, and the contract
     * refuses to state a demand the source can never satisfy.
     */
    private void validateEvidence(ArtifactKind artifactKind, PracticeAutomatedReviewPolicy requirements) {
        Set<SourceKind> applicable = sourceCatalogs.requireSourcesFor(
            requirements.sourceContractVersion(),
            artifactKind.value()
        );
        for (PracticeEvidenceRequirement need : requirements.needs()) {
            sourceCatalogs.requireSource(requirements.sourceContractVersion(), need.sourceKind());
            if (!applicable.contains(need.sourceKind())) {
                throw new IllegalArgumentException("Evidence source is not available for the selected work type");
            }
        }
    }
}
