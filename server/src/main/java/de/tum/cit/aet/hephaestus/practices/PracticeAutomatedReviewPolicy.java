package de.tum.cit.aet.hephaestus.practices;

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
    @Valid
    @Schema(description = "Automated review configuration; human review is a separate process")
    PracticeAutomatedReview automatedReview,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Sources this practice reads, each with the stance it takes towards that source")
    List<PracticeEvidenceRequirement> needs,
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
        Objects.requireNonNull(automatedReview, "automatedReview");
        needs = Objects.requireNonNull(needs, "needs")
            .stream()
            .sorted(Comparator.comparing(need -> need.sourceKind().value()))
            .toList();
        Set<String> seen = new HashSet<>();
        for (PracticeEvidenceRequirement need : needs) {
            if (!seen.add(need.sourceKind().value())) {
                throw new IllegalArgumentException("needs contains duplicate source " + need.sourceKind());
            }
        }
        knownLimitations = Objects.requireNonNull(knownLimitations, "knownLimitations")
            .stream()
            .sorted(Comparator.comparing(PracticeEvidenceLimitation::code))
            .toList();
        boolean automatedReviewDisabled = automatedReview.mode() == PracticeAutomatedReviewMode.NONE;
        if (automatedReviewDisabled && (!needs.isEmpty() || !knownLimitations.isEmpty())) {
            throw new IllegalArgumentException(
                "A practice without automated review cannot declare review evidence or limitations"
            );
        }
        // Contextual sources alone would let a review start having read nothing it must read, and every
        // verdict it then produced would be about evidence it never established it had.
        if (!automatedReviewDisabled && needs.stream().noneMatch(PracticeEvidenceRequirement::isRequired)) {
            throw new IllegalArgumentException("Automated review requires at least one required evidence source");
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

    /** The sources a refusal can be about, in source order. */
    public List<PracticeEvidenceRequirement> requiredNeeds() {
        return needs.stream().filter(PracticeEvidenceRequirement::isRequired).toList();
    }
}
