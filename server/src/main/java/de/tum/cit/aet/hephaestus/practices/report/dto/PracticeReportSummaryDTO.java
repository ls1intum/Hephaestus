package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One developer on the mentor roster: who they are, where they stand on each practice area, and whether a
 * mentor should look. Admin/owner-only, because it names individuals.
 *
 * <p>No score, no rank, no total: the only ordering signal is {@code needsAttention}, whose reasons are
 * spelled out in words rather than as a number a mentor would compare across rows.
 */
@Schema(
    description = "A developer on the mentor roster (admin/owner-only), sorted so people who may need support come first"
)
public record PracticeReportSummaryDTO(
    @NonNull @Schema(description = "Stable SCM user id for drill-down calls") Long userId,
    @NonNull @Schema(description = "Developer login") String userLogin,
    @Nullable @Schema(description = "Developer display name (may be null; UI falls back to login)") String name,
    @Nullable @Schema(description = "Developer avatar URL") String avatarUrl,
    @NonNull @Schema(description = "The developer's status on each practice area") List<AreaStatusCellDTO> areas,
    @Schema(description = "Whether the developer has unresolved gaps a mentor should look at (a triage flag)")
    boolean needsAttention,
    @NonNull
    @Schema(description = "Plain-language reasons behind needsAttention (empty when none)")
    List<String> attentionReasons
) {}
