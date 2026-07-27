package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The instance-admin cross-tenant rollup for one month.
 *
 * <p>An envelope rather than a bare array because {@code month} and {@code fx} are facts about the
 * REQUEST, not about any workspace in it: one month resolves to exactly one rate, so carrying them
 * per row would repeat one value N times and force a client to reach into {@code rows[0]} for a
 * response-level fact. Here there is exactly one place each of them can be read from.
 */
@Schema(description = "Instance-admin per-workspace month rollup (metadata only, no tenant content)")
public record AdminLlmUsageReportDTO(
    @NonNull @Schema(description = "Calendar month (UTC), ISO yyyy-MM", example = "2026-07") String month,
    @Nullable
    @Schema(
        description = "Display-only conversion when the instance has a display currency. " +
            "Absent = show USD only. Applies to every USD amount in this response."
    )
    FxRateInfoDTO fx,
    @NonNull
    @Schema(description = "One row per workspace with ledger activity this month")
    List<AdminWorkspaceLlmUsageDTO> workspaces
) {}
