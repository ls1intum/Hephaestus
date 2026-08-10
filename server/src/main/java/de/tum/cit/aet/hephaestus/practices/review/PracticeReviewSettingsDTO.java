package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * A workspace's practice-review policy.
 *
 * <p>Each knob is exposed twice: the <em>effective</em> value (override-or-fleet-default) drives the
 * control, and the raw <em>override</em> (null = inheriting) lets the UI mark inherited fields.
 */
@Schema(description = "A workspace's practice-review policy: effective values plus raw overrides")
public record PracticeReviewSettingsDTO(
    @NonNull @Schema(description = "Effective: run practice review for all developers") Boolean runForAllUsers,
    @NonNull @Schema(description = "Effective: deliver feedback to merged PRs/MRs") Boolean deliverToMerged,
    @NonNull
    @Schema(description = "Effective: minimum minutes between reviews for the same PR")
    Integer cooldownMinutes,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean runForAllUsersOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Boolean deliverToMergedOverride,
    @Schema(description = "Raw override; null = inheriting the fleet default") Integer cooldownMinutesOverride,
    @NonNull
    @Schema(
        description = "Which work is reviewed at all, ANDed onto every practice binding. Empty lists mean " +
            "no restriction on that axis. Exact names only — no patterns, and no path scope (changed paths " +
            "are not known where the decision is made)."
    )
    WorkspaceReviewScope reviewScope,
    @NonNull
    @Schema(
        description = "Effective: how much autonomy the system has over practices and areas that hold no " +
            "tier of their own — the bottom of the practice → area → workspace chain"
    )
    PracticeReviewTier defaultReviewTier,
    @Schema(description = "Raw override; null = this workspace has never chosen, so DELIVER applies")
    PracticeReviewTier defaultReviewTierOverride,
    @NonNull
    @Schema(
        description = "Effective: where feedback may go at all. CONVERSATION = the recipient's mentor " +
            "conversation and nowhere else · ON_THE_WORK = also on the work itself, as pull-request " +
            "summaries, inline notes and issue comments. ANDed with every practice's tier, so this cannot " +
            "make a quiet practice speak — only stop a loud one from speaking in a given place."
    )
    FeedbackReach feedbackReach,
    @Schema(description = "Raw override; null = this workspace has never chosen, so ON_THE_WORK applies")
    FeedbackReach feedbackReachOverride
) {}
