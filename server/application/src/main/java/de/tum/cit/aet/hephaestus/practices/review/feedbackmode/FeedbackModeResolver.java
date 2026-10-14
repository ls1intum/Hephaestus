package de.tum.cit.aet.hephaestus.practices.review.feedbackmode;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeSource;
import org.jspecify.annotations.Nullable;

public final class FeedbackModeResolver {
    private FeedbackModeResolver() {}

    public static EffectiveFeedbackMode resolve(
            @Nullable FeedbackMode practice, @Nullable FeedbackMode group, @Nullable FeedbackMode workspace) {
        if (practice != null) return new EffectiveFeedbackMode(practice, FeedbackModeSource.PRACTICE);
        if (group != null) return new EffectiveFeedbackMode(group, FeedbackModeSource.GROUP);
        if (workspace != null) return new EffectiveFeedbackMode(workspace, FeedbackModeSource.WORKSPACE);
        return new EffectiveFeedbackMode(FeedbackMode.APPROVE_FIRST, FeedbackModeSource.INSTANCE);
    }

    public static EffectiveFeedbackMode resolvePractice(Practice practice, @Nullable FeedbackMode workspace) {
        PracticeGroup group = practice.getGroup();
        return resolve(practice.getFeedbackMode(), group == null ? null : group.getFeedbackMode(), workspace);
    }

    public static EffectiveFeedbackMode resolveGroup(@Nullable PracticeGroup group, @Nullable FeedbackMode workspace) {
        return resolve(null, group == null ? null : group.getFeedbackMode(), workspace);
    }

    public static FeedbackMode effectiveModeOf(Practice practice, @Nullable FeedbackMode workspace) {
        return resolvePractice(practice, workspace).mode();
    }
}
