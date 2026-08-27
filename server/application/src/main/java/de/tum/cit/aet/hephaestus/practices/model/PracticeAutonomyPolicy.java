package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import org.jspecify.annotations.Nullable;

public final class PracticeAutonomyPolicy {

    private PracticeAutonomyPolicy() {}

    public static boolean delivers(
            ObservationOrigin origin, @Nullable PracticeAutonomy autonomy, FeedbackChannel channel) {
        if (!origin.delivers(channel)) {
            return false;
        }
        return autonomy != null && autonomy.deliversWithoutApproval();
    }
}
