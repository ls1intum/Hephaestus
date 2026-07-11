package de.tum.cit.aet.hephaestus.practices.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One developer on the mentor roster: their identity plus a per-AREA standing (rolled up across all practice
 * areas, P1 generalisation) and a needs-attention triage flag. Admin/owner-only (it names an individual).
 */
@Schema(description = "A developer on the mentor roster (admin/owner-only; a triage view, never a ranking)")
public record PracticeReportSummaryDTO(
    @NonNull @Schema(description = "Stable SCM user id for drill-down calls") Long userId,
    @NonNull @Schema(description = "Developer login") String userLogin,
    @Nullable @Schema(description = "Developer display name (may be null; UI falls back to login)") String name,
    @NonNull @Schema(description = "Developer avatar URL") String avatarUrl,
    @NonNull @Schema(description = "The developer's status on each practice area") List<AreaStatusCellDTO> areas,
    @Schema(description = "Whether the developer has unresolved gaps a mentor should look at (a triage flag)")
    boolean needsAttention,
    @NonNull
    @Schema(description = "Plain-language reasons behind needsAttention (empty when none)")
    List<String> attentionReasons
) {}
