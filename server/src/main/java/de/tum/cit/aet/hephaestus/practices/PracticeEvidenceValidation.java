package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Independent validation status and provenance for an evidence declaration")
public record PracticeEvidenceValidation(
    @NonNull PracticeEvidenceValidationStatus status,
    @NonNull SourceContractVersion sourceContractVersion,
    @NonNull String declarationDigest,
    @Nullable String validator,
    @Nullable Instant validatedAt,
    @Nullable String validationReference
) {
    public PracticeEvidenceValidation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(declarationDigest, "declarationDigest");
        if (!declarationDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid declaration digest");
        }
        if (status == PracticeEvidenceValidationStatus.AUTHOR_DECLARED) {
            if (validator != null || validatedAt != null || validationReference != null) {
                throw new IllegalArgumentException("Author declarations cannot carry validation provenance");
            }
        } else if (validator == null || validatedAt == null || validationReference == null) {
            throw new IllegalArgumentException("Reviewed validation status requires complete provenance");
        }
        if (validator != null && validator.isBlank()) {
            throw new IllegalArgumentException("validator must be null or non-blank");
        }
        if (validationReference != null && validationReference.isBlank()) {
            throw new IllegalArgumentException("validationReference must be null or non-blank");
        }
    }

    public static PracticeEvidenceValidation authorDeclared(PracticeEvidenceDeclaration declaration) {
        Objects.requireNonNull(declaration, "declaration");
        return new PracticeEvidenceValidation(
            PracticeEvidenceValidationStatus.AUTHOR_DECLARED,
            declaration.sourceContractVersion(),
            PracticeEvidenceDigest.digest(declaration),
            null,
            null,
            null
        );
    }
}
