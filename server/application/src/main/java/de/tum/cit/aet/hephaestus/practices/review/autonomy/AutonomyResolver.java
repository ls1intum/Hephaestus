package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import org.jspecify.annotations.Nullable;

public final class AutonomyResolver {

    private AutonomyResolver() {}

    public static PracticeAutonomy workspaceDefault(@Nullable PracticeAutonomy workspaceOverride) {
        return workspaceOverride != null ? workspaceOverride : PracticeAutonomy.DEFAULT;
    }

    public static EffectiveAutonomy resolveGroup(
            @Nullable PracticeAutonomy groupAutonomy, PracticeAutonomy workspaceDefault) {
        return groupAutonomy != null
                ? new EffectiveAutonomy(groupAutonomy, AutonomySource.GROUP)
                : new EffectiveAutonomy(workspaceDefault, AutonomySource.WORKSPACE);
    }

    public static EffectiveAutonomy resolveGroup(@Nullable PracticeGroup group, PracticeAutonomy workspaceDefault) {
        return resolveGroup(group == null ? null : group.getAutonomy(), workspaceDefault);
    }

    public static EffectiveAutonomy resolvePractice(
            @Nullable PracticeAutonomy practiceAutonomy,
            @Nullable PracticeAutonomy groupAutonomy,
            PracticeAutonomy workspaceDefault) {
        if (practiceAutonomy != null) {
            return new EffectiveAutonomy(practiceAutonomy, AutonomySource.PRACTICE);
        }
        return resolveGroup(groupAutonomy, workspaceDefault);
    }

    public static EffectiveAutonomy resolvePractice(Practice practice, PracticeAutonomy workspaceDefault) {
        PracticeGroup group = practice.getGroup();
        return resolvePractice(practice.getAutonomy(), group == null ? null : group.getAutonomy(), workspaceDefault);
    }

    public static PracticeAutonomy effectiveAutonomyOf(Practice practice, PracticeAutonomy workspaceDefault) {
        return resolvePractice(practice, workspaceDefault).autonomy();
    }
}
