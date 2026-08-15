package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

/**
 * The two answers a workspace gives about its own practice reviews: how much autonomy the system has where
 * nothing below has decided, and where feedback may go at all.
 *
 * <p>{@link PracticeReviewSettings} keeps both as {@code String} columns because it lives in the workspace
 * module, which the practices module already depends on — naming {@link PracticeReviewTier} there would
 * close a module cycle. The conversion happens here, once; every consumer of a workspace-level review
 * default goes through this record rather than reading the columns.
 *
 * <p>An unparseable stored name throws rather than falling back to {@link PracticeReviewTier#DEFAULT}: that
 * fallback is the loudest tier, so a vocabulary the database and the code disagreed about would quietly make
 * a workspace louder than it asked to be.
 */
public record WorkspaceReviewDefaults(PracticeReviewTier defaultTier, FeedbackReach reach) {
    /** What a workspace that has never expressed an opinion gets. */
    public static final WorkspaceReviewDefaults UNSET = new WorkspaceReviewDefaults(
        PracticeReviewTier.DEFAULT,
        FeedbackReach.DEFAULT
    );

    public static WorkspaceReviewDefaults of(Workspace workspace) {
        return of(workspace.getReviewSettings());
    }

    public static WorkspaceReviewDefaults of(@Nullable PracticeReviewSettings settings) {
        if (settings == null) {
            return UNSET;
        }
        return new WorkspaceReviewDefaults(
            ReviewTierResolver.workspaceDefault(tier(settings.getDefaultReviewTier())),
            reach(settings.getFeedbackReach())
        );
    }

    private static @Nullable PracticeReviewTier tier(@Nullable String name) {
        return name == null ? null : PracticeReviewTier.valueOf(name);
    }

    private static FeedbackReach reach(@Nullable String name) {
        return name == null ? FeedbackReach.DEFAULT : FeedbackReach.valueOf(name);
    }
}
