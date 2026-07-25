package de.tum.cit.aet.hephaestus.practices.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * A workspace's practice-review policy.
 *
 * <p>Each knob is exposed twice: the <em>effective</em> value (what actually happens,
 * override-or-fleet-default) drives the control, and the raw <em>override</em> (null = inheriting)
 * lets the UI mark inherited fields and offer a reset.
 *
 * <p>Deliberately carries nothing else. The workspace's feature flags live on {@code WorkspaceDTO},
 * which every client already holds, and whether the workspace may register its own provider is a
 * property of its LLM connections, reported by {@code GET /workspaces/{slug}/llm/settings}. Copying
 * either one here is what turned this endpoint's predecessor into a grab bag named for a thing it no
 * longer contained.
 */
@Schema(description = "A workspace's practice-review policy: effective values plus raw overrides")
public record PracticeReviewSettingsDTO(
    @NonNull @Schema(description = "Effective: run practice review for all developers") Boolean runForAllUsers,
    @NonNull @Schema(description = "Effective: skip draft PRs/MRs") Boolean skipDrafts,
    @NonNull @Schema(description = "Effective: deliver feedback to merged PRs/MRs") Boolean deliverToMerged,
    @NonNull
    @Schema(description = "Effective: minimum minutes between reviews for the same PR")
    Integer cooldownMinutes,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean runForAllUsersOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean skipDraftsOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean deliverToMergedOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Integer cooldownMinutesOverride
) {}
