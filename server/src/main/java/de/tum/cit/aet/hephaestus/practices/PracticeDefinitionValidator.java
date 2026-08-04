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
        validate(
            definition.artifactType(),
            definition.triggerEvents(),
            definition.whyItMatters(),
            definition.whatGoodLooksLike()
        );
        validateEvidence(definition.artifactType(), definition.automatedAssessmentPolicy());
    }

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

    private void validateEvidence(WorkArtifact artifactType, PracticeAutomatedAssessmentPolicy requirements) {
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

    private void validateOptionalRequirements(EvidenceProfile profile, PracticeAutomatedAssessmentPolicy requirements) {
        for (PracticeOptionalContextSource requirement : requirements.optionalContext()) {
            sourceCatalogs.requireSource(requirements.sourceContractVersion(), requirement.sourceKind());
            if (!profile.allows(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence source is not allowed by the selected profile");
            }
        }
    }

    private void validateRequirements(
        EvidenceProfile profile,
        PracticeAutomatedAssessmentPolicy automatedAssessmentPolicy,
        List<PracticeEvidenceRequirement> sourceRequirements
    ) {
        for (PracticeEvidenceRequirement requirement : sourceRequirements) {
            ArtifactSourceContract source = sourceCatalogs.requireSource(
                automatedAssessmentPolicy.sourceContractVersion(),
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
