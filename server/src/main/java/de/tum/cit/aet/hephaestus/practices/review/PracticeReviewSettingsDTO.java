package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A workspace's practice-review policy.
 *
 * <p>Each knob is exposed twice: the <em>effective</em> value (override-or-fleet-default) drives the
 * control, and the raw <em>override</em> (null = inheriting) lets the UI mark inherited fields.
 */
@Schema(description = "A workspace's practice-review policy: effective values plus raw overrides")
public record PracticeReviewSettingsDTO(
    @NonNull @Schema(description = "Effective: deliver feedback to merged PRs/MRs") Boolean deliverToMerged,
    @NonNull
    @Schema(description = "Effective: minimum minutes between reviews for the same PR")
    Integer cooldownMinutes,
    @Schema(description = "Raw override; null = inheriting the fleet default")
    @Nullable
    Boolean deliverToMergedOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default")
    @Nullable
    Integer cooldownMinutesOverride,
    @NonNull
    @Schema(
        description = "Which work is reviewed at all, ANDed onto every practice binding. Empty lists mean " +
            "no restriction on that axis. Exact names only — no patterns, and no path scope (changed paths " +
            "are not known where the decision is made)."
    )
    WorkspaceReviewScope reviewScope,
    @NonNull
    @Schema(
        description = "Effective: how much autonomy the system has over practices and groups that hold no " +
            "autonomy of their own — the bottom of the practice → group → workspace chain"
    )
    PracticeAutonomy defaultAutonomy,
    @Schema(description = "Raw override; null = this workspace has never chosen, so HUMAN_APPROVAL applies")
    @Nullable
    PracticeAutonomy defaultAutonomyOverride
) {}
