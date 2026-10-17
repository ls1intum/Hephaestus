package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

public record WorkspaceReviewDefaults(FeedbackMode defaultFeedbackMode) {
    public static final WorkspaceReviewDefaults UNSET = new WorkspaceReviewDefaults(FeedbackMode.APPROVE_FIRST);

    public static WorkspaceReviewDefaults of(@Nullable Workspace workspace) {
        return workspace == null ? UNSET : of(workspace.getReviewSettings());
    }

    public static WorkspaceReviewDefaults of(@Nullable PracticeReviewSettings settings) {
        return settings == null || settings.getDefaultFeedbackMode() == null
                ? UNSET
                : new WorkspaceReviewDefaults(FeedbackMode.valueOf(settings.getDefaultFeedbackMode()));
    }
}
