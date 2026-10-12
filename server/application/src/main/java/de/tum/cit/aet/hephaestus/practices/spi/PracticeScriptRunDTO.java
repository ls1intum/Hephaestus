package de.tum.cit.aet.hephaestus.practices.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "What one practice's program did in one review")
public record PracticeScriptRunDTO(
        @NonNull PracticeScriptRunStatus status,

        @NonNull @Schema(description = "The built-in review, or the practice's own script")
        PracticeProgramKind programKind,

        @Schema(
                description = "How the program reached its result, from the calls it made. Absent when the program "
                        + "did not run, or the runner did not report its calls.")
        @Nullable
        PracticeProgramPath path,

        @Schema(description = "Why the program stopped without a result. Absent when it returned one.") @Nullable
        PracticeScriptStopReason stopReason,

        @NonNull
        @Schema(
                description = "The direct models the program declared and what became of their calls. Empty for the "
                        + "built-in program, for a script that declares none, and for one that ended before it "
                        + "declared them.")
        List<PracticeScriptModelUseDTO> models) {}
