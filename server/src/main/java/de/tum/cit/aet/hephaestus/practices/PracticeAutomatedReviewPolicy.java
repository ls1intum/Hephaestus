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
import org.jspecify.annotations.Nullable;

@Schema(description = "Author-defined automated review and evidence requirements for one practice revision")
public record PracticeAutomatedReviewPolicy(
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
    @Schema(description = "Automated review configuration; human review is a separate process")
    PracticeAutomatedReview automatedReview,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources that must meet their quality requirements before automated review may start")
    List<PracticeEvidenceRequirement> requiredEvidence,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources that may add context but never block automated review when absent")
    List<PracticeOptionalContextSource> optionalContext,
    @NonNull
    @NotNull
    @Schema(description = "Conservative action when required evidence does not pass")
    PracticeInsufficientEvidenceAction whenEvidenceIsInsufficient,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Claims the selected evidence cannot support even when every requirement passes")
    List<PracticeEvidenceLimitation> knownLimitations,
    @Nullable
    @Valid
    @Schema(
        description = "Why this practice needs a human rather than automated review; present only when " +
            "evidenceSufficiency is DECLARED_EVIDENCE_INSUFFICIENT"
    )
    PracticeEvidenceLimitation insufficiencyReason
) {
    public PracticeAutomatedReviewPolicy {
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(evidenceProfile, "evidenceProfile");
        Objects.requireNonNull(automatedReview, "automatedReview");
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
        boolean automatedReviewDisabled = automatedReview.mode() == PracticeAutomatedReviewMode.NONE;
        if (
            automatedReviewDisabled &&
            (!requiredEvidence.isEmpty() || !optionalContext.isEmpty() || !knownLimitations.isEmpty())
        ) {
            throw new IllegalArgumentException(
                "A practice without automated review cannot declare review evidence or limitations"
            );
        }
        if (!automatedReviewDisabled && requiredEvidence.isEmpty()) {
            throw new IllegalArgumentException("Automated review requires at least one evidence source");
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
        // A known limitation is what the evidence cannot show; this is why no automated review runs at
        // all. Sharing one list conflated the two and left the reason identified only by its position.
        boolean declaredInsufficient =
            automatedReview.evidenceSufficiency() == PracticeEvidenceSufficiency.DECLARED_EVIDENCE_INSUFFICIENT;
        if (declaredInsufficient && insufficiencyReason == null) {
            throw new IllegalArgumentException("Insufficient evidence must state why a human is needed");
        }
        if (!declaredInsufficient && insufficiencyReason != null) {
            throw new IllegalArgumentException("Only insufficient evidence carries a reason a human is needed");
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
