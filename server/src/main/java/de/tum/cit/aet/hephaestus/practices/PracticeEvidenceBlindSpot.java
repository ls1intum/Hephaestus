package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Author-declared limitation that remains when required evidence is available")
public record PracticeEvidenceBlindSpot(
    @NonNull @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}", message = "Code must use uppercase snake case") String code,
    @NonNull @NotBlank @Size(max = 500) String summary
) {
    public PracticeEvidenceBlindSpot {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(summary, "summary");
    }
}
