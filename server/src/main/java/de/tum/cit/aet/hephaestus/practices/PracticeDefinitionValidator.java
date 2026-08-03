package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceContract;
import de.tum.cit.aet.hephaestus.evidence.EvidenceProfile;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
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

    private static final Pattern BLIND_SPOT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

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
        validateEvidence(definition.artifactType(), definition.evidence());
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

    private void validateEvidence(WorkArtifact artifactType, PracticeEvidenceDeclaration declaration) {
        EvidenceProfile profile = sourceCatalogs.requireProfile(
            declaration.sourceContractVersion(),
            declaration.profile()
        );
        if (!profile.artifactType().equals(artifactType.name())) {
            throw new IllegalArgumentException("Evidence profile is not available for the selected work type");
        }
        if (declaration.required().isEmpty()) {
            throw new IllegalArgumentException("Declare at least one required evidence source");
        }

        Set<SourceKind> required = validateRequirements(profile, declaration, declaration.required());
        Set<SourceKind> optional = validateOptionalRequirements(profile, declaration);
        if (required.stream().anyMatch(optional::contains)) {
            throw new IllegalArgumentException("An evidence source cannot be both required and optional");
        }

        Set<String> blindSpotCodes = new HashSet<>();
        for (PracticeEvidenceBlindSpot blindSpot : declaration.blindSpots()) {
            if (!BLIND_SPOT_CODE.matcher(blindSpot.code()).matches()) {
                throw new IllegalArgumentException("Blind-spot codes must use uppercase snake case");
            }
            if (!blindSpotCodes.add(blindSpot.code())) {
                throw new IllegalArgumentException("Blind-spot codes must not contain duplicates");
            }
            if (blindSpot.summary().isBlank() || blindSpot.summary().length() > 500) {
                throw new IllegalArgumentException("Blind-spot summaries must contain 1 to 500 characters");
            }
        }
    }

    private Set<SourceKind> validateOptionalRequirements(
        EvidenceProfile profile,
        PracticeEvidenceDeclaration declaration
    ) {
        Set<SourceKind> kinds = new HashSet<>();
        for (OptionalPracticeEvidenceRequirement requirement : declaration.optional()) {
            if (!kinds.add(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence requirements must not contain duplicate source kinds");
            }
            sourceCatalogs.requireSource(declaration.sourceContractVersion(), requirement.sourceKind());
            if (!profile.allows(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence source is not allowed by the selected profile");
            }
        }
        return kinds;
    }

    private Set<SourceKind> validateRequirements(
        EvidenceProfile profile,
        PracticeEvidenceDeclaration declaration,
        List<PracticeEvidenceRequirement> requirements
    ) {
        Set<SourceKind> kinds = new HashSet<>();
        for (PracticeEvidenceRequirement requirement : requirements) {
            if (!kinds.add(requirement.sourceKind())) {
                throw new IllegalArgumentException("Evidence requirements must not contain duplicate source kinds");
            }
            ArtifactSourceContract source = sourceCatalogs.requireSource(
                declaration.sourceContractVersion(),
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
        return kinds;
    }
}
