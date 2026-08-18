package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

/**
 * What a workspace answers about its own practice reviews: how much autonomy the system has where nothing
 * below has decided.
 *
 * <p>{@link PracticeReviewSettings} keeps it as a {@code String} column because it lives in the workspace
 * module, which the practices module already depends on — naming {@link PracticeReviewTier} there would
 * close a module cycle. The conversion happens here, once; every consumer of a workspace-level review
 * default goes through this record rather than reading the column.
 *
 * <p>An unparseable stored name throws rather than falling back to {@link PracticeReviewTier#DEFAULT}: that
 * fallback is the loudest tier, so a vocabulary the database and the code disagreed about would quietly make
 * a workspace louder than it asked to be.
 */
public record WorkspaceReviewDefaults(PracticeReviewTier defaultTier) {
    /** What a workspace that has never expressed an opinion gets. */
    public static final WorkspaceReviewDefaults UNSET = new WorkspaceReviewDefaults(PracticeReviewTier.DEFAULT);

    public static WorkspaceReviewDefaults of(Workspace workspace) {
        return of(workspace.getReviewSettings());
    }

    public static WorkspaceReviewDefaults of(@Nullable PracticeReviewSettings settings) {
        if (settings == null) {
            return UNSET;
        }
        return new WorkspaceReviewDefaults(ReviewTierResolver.workspaceDefault(tier(settings.getDefaultReviewTier())));
    }

    private static @Nullable PracticeReviewTier tier(@Nullable String name) {
        return name == null ? null : PracticeReviewTier.valueOf(name);
    }
}
