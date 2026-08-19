package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

public record WorkspaceReviewDefaults(PracticeAutonomy defaultAutonomy) {
    public static final WorkspaceReviewDefaults UNSET = new WorkspaceReviewDefaults(PracticeAutonomy.DEFAULT);

    public static WorkspaceReviewDefaults of(Workspace workspace) {
        return of(workspace.getReviewSettings());
    }

    public static WorkspaceReviewDefaults of(@Nullable PracticeReviewSettings settings) {
        if (settings == null) {
            return UNSET;
        }
        return new WorkspaceReviewDefaults(AutonomyResolver.workspaceDefault(autonomy(settings.getDefaultAutonomy())));
    }

    private static @Nullable PracticeAutonomy autonomy(@Nullable String name) {
        return name == null ? null : PracticeAutonomy.valueOf(name);
    }
}
