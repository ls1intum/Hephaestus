package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "One model a practice's script needs, and for which members no model is ready")
public record PracticeScriptNeedDTO(
        @NonNull @Schema(description = "The purpose whose binding serves this model")
        PracticeScriptModelPurpose purpose,

        @NonNull PracticeScriptNeed need,

        @NonNull
        @Schema(
                description = "The member tiers that no ready binding of this purpose serves today. UNDECLARED "
                        + "appears only when members may leave their AI choice open.")
        List<DataHandlingTier> unmetTiers) {}
