package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Independent validation status and provenance for automated review requirements")
public record PracticeAutomatedReviewValidation(
    @NonNull
    @Schema(
        description = "Validation lifecycle; authors cannot mark their own review policy as independently validated"
    )
    PracticeAutomatedReviewValidationStatus status,
    @NonNull
    @Schema(description = "Source contract used by the validated practice definition")
    SourceContractVersion sourceContractVersion,
    @NonNull @Schema(description = "SHA-256 digest of the exact automated-review policy") String policyDigest,
    @NonNull @Schema(description = "Versioned fingerprint of the exact review rules") String reviewRuleFingerprint,
    @Nullable
    @Schema(
        description = "Versioned fingerprint of the independently validated model, prompt, tools, and preprocessing"
    )
    String evaluatorProcedureFingerprint,
    @Nullable @Schema(description = "Independent validator identity") String validator,
    @Nullable @Schema(description = "Time the independent validation was completed") Instant validatedAt,
    @Nullable @Schema(description = "Traceable reference to the validation record") String validationReference
) {
    public PracticeAutomatedReviewValidation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(policyDigest, "policyDigest");
        Objects.requireNonNull(reviewRuleFingerprint, "reviewRuleFingerprint");
        if (!policyDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid policy digest");
        }
        if (!reviewRuleFingerprint.matches("v[0-9]+:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid review-rule fingerprint");
        }
        if (status == PracticeAutomatedReviewValidationStatus.AUTHOR_DECLARED) {
            if (
                evaluatorProcedureFingerprint != null ||
                validator != null ||
                validatedAt != null ||
                validationReference != null
            ) {
                throw new IllegalArgumentException("Author declarations cannot carry validation provenance");
            }
        } else if (
            evaluatorProcedureFingerprint == null ||
            validator == null ||
            validatedAt == null ||
            validationReference == null
        ) {
            throw new IllegalArgumentException("Reviewed validation status requires complete provenance");
        }
        if (evaluatorProcedureFingerprint != null && !evaluatorProcedureFingerprint.matches("v[0-9]+:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid evaluator-procedure fingerprint");
        }
        if (validator != null && validator.isBlank()) {
            throw new IllegalArgumentException("validator must be null or non-blank");
        }
        if (validationReference != null && validationReference.isBlank()) {
            throw new IllegalArgumentException("validationReference must be null or non-blank");
        }
    }

    public static PracticeAutomatedReviewValidation authorDeclared(String practiceSlug, PracticeDefinition definition) {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(definition, "definition");
        PracticeAutomatedReviewPolicy requirements = definition.automatedReviewPolicy();
        return new PracticeAutomatedReviewValidation(
            PracticeAutomatedReviewValidationStatus.AUTHOR_DECLARED,
            requirements.sourceContractVersion(),
            PracticeAutomatedReviewPolicyDigest.digest(requirements),
            definition.provenanceFingerprint(practiceSlug),
            null,
            null,
            null,
            null
        );
    }
}
