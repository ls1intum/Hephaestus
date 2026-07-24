package de.tum.cit.aet.hephaestus.agent.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Aggregate read model for the workspace AI-settings admin page. Thin by design: bindings (config
 * ids) + the practice-review policy + feature flags, but NOT the config list — the UI resolves
 * config id → name against {@code GET /agent-configs}, keeping one source of truth.
 *
 * <p>Each practice-review knob is exposed twice: the <em>effective</em> value (what actually
 * happens, override-or-fleet-default) drives the control, and the raw <em>override</em> (null =
 * inheriting) lets the UI mark inherited fields and offer a reset.
 */
@Schema(
    description = "Aggregate workspace AI settings: runtime bindings + effective + raw-override practice-review policy"
)
public record AiSettingsViewDTO(
    @Schema(description = "Config bound to power practice detection (null = fan-out to all enabled configs)")
    Long practiceConfigId,
    @Schema(description = "Config explicitly bound to power the mentor (null = mentor is unconfigured)")
    Long mentorConfigId,
    @NonNull @Schema(description = "Effective: run practice review for all developers") Boolean runForAllUsers,
    @NonNull @Schema(description = "Effective: skip draft PRs/MRs") Boolean skipDrafts,
    @NonNull @Schema(description = "Effective: deliver feedback to merged PRs/MRs") Boolean deliverToMerged,
    @NonNull
    @Schema(description = "Effective: minimum minutes between reviews for the same PR")
    Integer cooldownMinutes,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean runForAllUsersOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean skipDraftsOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean deliverToMergedOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Integer cooldownMinutesOverride,
    @NonNull
    @Schema(description = "Whether the practices feature is enabled for this workspace")
    Boolean practicesEnabled,
    @NonNull @Schema(description = "Whether the mentor feature is enabled for this workspace") Boolean mentorEnabled,
    @NonNull
    @Schema(description = "Whether this workspace may register additional OpenAI-compatible connections")
    Boolean workspaceConnectionsAllowed
) {}
