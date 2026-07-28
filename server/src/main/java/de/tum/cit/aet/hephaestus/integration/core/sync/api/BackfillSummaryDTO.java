package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.BackfillSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Connection-level backfill rollup")
public record BackfillSummaryDTO(
    @NonNull @Schema(description = "Integration-defined backfill state string") String state,
    @Schema(description = "0-100 completion estimate, if computable") Integer percent
) {
    public static BackfillSummaryDTO from(BackfillSummary summary) {
        return new BackfillSummaryDTO(summary.state(), summary.percent());
    }
}
