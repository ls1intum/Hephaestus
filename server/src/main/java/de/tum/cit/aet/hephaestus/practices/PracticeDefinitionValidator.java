package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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

    public PracticeDefinitionValidator(ArtifactSourceCatalogRegistry sourceCatalogs) {
        this.sourceCatalogs = sourceCatalogs;
    }

    public void validate(PracticeDefinition definition) {
        boolean canRunAutomatedReview = definition
            .automatedReviewPolicy()
            .automatedReview()
            .canAttemptAutomatedReview();
        validateTriggers(definition.artifactType(), definition.triggerEvents(), canRunAutomatedReview);
        if (!canRunAutomatedReview && definition.precomputeScript() != null) {
            throw new IllegalArgumentException("A practice Hephaestus cannot review cannot define a precompute script");
        }
        rejectDetectorVocabulary("Why it matters", definition.whyItMatters());
        rejectDetectorVocabulary("What good looks like", definition.whatGoodLooksLike());
        validateEvidence(definition.artifactType(), definition.automatedReviewPolicy());
    }

    private static void validateTriggers(
        WorkArtifact artifactType,
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
        Set<String> allowed = TriggerEventCatalog.eligibleFor(artifactType);
        if (canRunAutomatedReview && artifactType != WorkArtifact.CONVERSATION_THREAD && triggerEvents.isEmpty()) {
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

    private void validateEvidence(WorkArtifact artifactType, PracticeAutomatedReviewPolicy requirements) {
        EvidenceProfile profile = sourceCatalogs.requireProfile(
            requirements.sourceContractVersion(),
            requirements.evidenceProfile()
        );
        if (!profile.artifactType().equals(artifactType.name())) {
            throw new IllegalArgumentException("Evidence profile is not available for the selected work type");
        }
        validateRequirements(profile, requirements, requirements.requiredEvidence());
        validateOptionalRequirements(profile, requirements);
    }

    private void validateOptionalRequirements(EvidenceProfile profile, PracticeAutomatedReviewPolicy requirements) {
        for (PracticeOptionalContextSource requirement : requirements.optionalContext()) {
            sourceCatalogs.requireSource(requirements.sourceContractVersion(), requirement.sourceKind());
            if (!profile.allows(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence source is not allowed by the selected profile");
            }
        }
    }

    private void validateRequirements(
        EvidenceProfile profile,
        PracticeAutomatedReviewPolicy automatedReviewPolicy,
        List<PracticeEvidenceRequirement> sourceRequirements
    ) {
        for (PracticeEvidenceRequirement requirement : sourceRequirements) {
            ArtifactSourceContract source = sourceCatalogs.requireSource(
                automatedReviewPolicy.sourceContractVersion(),
                requirement.sourceKind()
            );
            if (!profile.allows(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence source is not allowed by the selected profile");
            }
            if (
                requirement.completeness() == EvidenceCompletenessRequirement.COMPLETE &&
                !source.completenessPolicy().supportsComplete()
            ) {
                throw new IllegalArgumentException("Evidence source cannot satisfy COMPLETE requirements");
            }
            if (
                requirement.freshness() == EvidenceFreshnessRequirement.CURRENT &&
                !source.freshnessPolicy().supportsCurrentRequirement()
            ) {
                throw new IllegalArgumentException("Evidence source cannot satisfy CURRENT requirements");
            }
        }
    }
}
