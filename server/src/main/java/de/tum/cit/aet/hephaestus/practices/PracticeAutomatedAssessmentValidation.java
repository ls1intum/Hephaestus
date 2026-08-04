package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Independent validation status and provenance for automated assessment requirements")
public record PracticeAutomatedAssessmentValidation(
    @NonNull
    @Schema(description = "Validation lifecycle; authors cannot mark their own assessment as independently validated")
    PracticeAutomatedAssessmentValidationStatus status,
    @NonNull
    @Schema(description = "Source contract used by the validated practice definition")
    SourceContractVersion sourceContractVersion,
    @NonNull @Schema(description = "SHA-256 digest of the exact automated-assessment policy") String policyDigest,
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
    public PracticeAutomatedAssessmentValidation {
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
        if (status == PracticeAutomatedAssessmentValidationStatus.AUTHOR_DECLARED) {
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

    public static PracticeAutomatedAssessmentValidation authorDeclared(
        String practiceSlug,
        PracticeDefinition definition
    ) {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(definition, "definition");
        PracticeAutomatedAssessmentPolicy requirements = definition.automatedAssessmentPolicy();
        return new PracticeAutomatedAssessmentValidation(
            PracticeAutomatedAssessmentValidationStatus.AUTHOR_DECLARED,
            requirements.sourceContractVersion(),
            PracticeAutomatedAssessmentPolicyDigest.digest(requirements),
            definition.provenanceFingerprint(practiceSlug),
            null,
            null,
            null,
            null
        );
    }
}
