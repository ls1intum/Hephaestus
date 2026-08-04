package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

@Schema(description = "Author-defined automated assessment and evidence requirements for one practice revision")
public record PracticeAutomatedAssessmentPolicy(
    @NonNull
    @NotNull
    @Schema(description = "Exact contract version that defines source kinds and source-state semantics")
    SourceContractVersion sourceContractVersion,
    @NonNull
    @NotNull
    @Schema(description = "Set of evidence sources allowed for this type of reviewed work")
    EvidenceProfileId evidenceProfile,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Automated assessment configuration; human assessment is a separate process")
    PracticeAutomatedAssessment automatedAssessment,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources that must meet their quality requirements before assessment may start")
    List<PracticeEvidenceRequirement> requiredEvidence,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources that may add context but never block assessment when absent")
    List<PracticeOptionalContextSource> optionalContext,
    @NonNull
    @NotNull
    @Schema(description = "Conservative action when required evidence does not pass")
    PracticeInsufficientEvidenceAction whenEvidenceIsInsufficient,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Claims the selected evidence cannot support even when every requirement passes")
    List<PracticeEvidenceLimitation> knownLimitations
) {
    public PracticeAutomatedAssessmentPolicy {
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(evidenceProfile, "evidenceProfile");
        Objects.requireNonNull(automatedAssessment, "automatedAssessment");
        requiredEvidence = sortedRequirements(requiredEvidence, "requiredEvidence");
        optionalContext = Objects.requireNonNull(optionalContext, "optionalContext")
            .stream()
            .sorted(Comparator.comparing(requirement -> requirement.sourceKind().value()))
            .toList();
        Set<String> requiredKinds = uniqueSourceKinds(
            requiredEvidence
                .stream()
                .map(requirement -> requirement.sourceKind().value())
                .toList(),
            "requiredEvidence"
        );
        Set<String> optionalKinds = uniqueSourceKinds(
            optionalContext
                .stream()
                .map(requirement -> requirement.sourceKind().value())
                .toList(),
            "optionalContext"
        );
        knownLimitations = Objects.requireNonNull(knownLimitations, "knownLimitations")
            .stream()
            .sorted(Comparator.comparing(PracticeEvidenceLimitation::code))
            .toList();
        if (requiredKinds.stream().anyMatch(optionalKinds::contains)) {
            throw new IllegalArgumentException("A source cannot be both required evidence and optional context");
        }
        boolean assessmentAbsent = automatedAssessment.mode() == PracticeAutomatedAssessmentMode.NONE;
        if (
            assessmentAbsent &&
            (!requiredEvidence.isEmpty() || !optionalContext.isEmpty() || !knownLimitations.isEmpty())
        ) {
            throw new IllegalArgumentException(
                "A practice without automated assessment cannot declare assessment evidence or limitations"
            );
        }
        if (!assessmentAbsent && requiredEvidence.isEmpty()) {
            throw new IllegalArgumentException("Automated assessment requires at least one evidence source");
        }
        Objects.requireNonNull(whenEvidenceIsInsufficient, "whenEvidenceIsInsufficient");
        Set<String> limitationCodes = new HashSet<>();
        if (
            knownLimitations
                .stream()
                .map(PracticeEvidenceLimitation::code)
                .anyMatch(code -> !limitationCodes.add(code))
        ) {
            throw new IllegalArgumentException("Known limitation codes must be unique");
        }
        if (
            automatedAssessment.evidenceSufficiency() == PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT &&
            knownLimitations.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Insufficient evidence must be explained by at least one known limitation"
            );
        }
    }

    private static Set<String> uniqueSourceKinds(List<String> sourceKinds, String field) {
        Set<String> kinds = new HashSet<>();
        for (String kind : sourceKinds) {
            if (!kinds.add(kind)) {
                throw new IllegalArgumentException(field + " contains duplicate source " + kind);
            }
        }
        return kinds;
    }

    private static List<PracticeEvidenceRequirement> sortedRequirements(
        List<PracticeEvidenceRequirement> requirements,
        String field
    ) {
        return Objects.requireNonNull(requirements, field)
            .stream()
            .sorted(Comparator.comparing(requirement -> requirement.sourceKind().value()))
            .toList();
    }
}
