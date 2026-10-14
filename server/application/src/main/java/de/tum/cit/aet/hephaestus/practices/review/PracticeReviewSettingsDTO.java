package de.tum.cit.aet.hephaestus.practices.review;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.WorkspaceReviewScopeDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A workspace's practice-review policy.
 *
 * <p>Each knob is exposed twice: the <em>effective</em> value (override-or-fleet-default) drives the
 * control, and the raw <em>override</em> (null = inheriting) lets the UI mark inherited fields.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "A workspace's practice-review policy: effective values plus raw overrides")
public record PracticeReviewSettingsDTO(
        @NonNull @Schema(description = "Strong entity tag to send in If-Match when updating this rollout")
        String etag,

        @NonNull @Schema(description = "Monotonic rollout revision carried by automatically admitted review jobs")
        String revision,

        @NonNull @Schema(description = "Effective: deliver feedback to merged PRs/MRs")
        Boolean deliverToMerged,

        @NonNull @Schema(description = "Effective: minimum minutes between reviews for the same PR")
        Integer cooldownMinutes,

        @Schema(description = "Raw override; null = inheriting the fleet default") @Nullable
        Boolean deliverToMergedOverride,

        @Schema(description = "Raw override; null = inheriting the fleet default") @Nullable
        Integer cooldownMinutesOverride,

        @NonNull
        @Schema(description = "Explicit all-or-selected repository and person coverage. Selected-empty means nobody.")
        WorkspaceReviewScopeDTO reviewScope,

        @NonNull @Schema(description = "Whether new external practice feedback may be sent")
        PracticeDeliveryStatus deliveryStatus,

        @NonNull PracticeReviewScopeSummaryDTO scopeSummary,

        @NonNull @Schema(description = "The default feedback mode when neither the practice nor its group selects one.")
        FeedbackMode defaultFeedbackMode,

        @Schema(description = "Raw override; null inherits APPROVE_FIRST") @Nullable
        FeedbackMode defaultFeedbackModeOverride,

        @NonNull
        @Schema(description = "Repository-root Ant-style generated-path patterns, keyed by monitored repository name")
        Map<String, List<String>> generatedPaths) {
    public PracticeReviewSettingsDTO {
        generatedPaths = generatedPaths.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }
}
