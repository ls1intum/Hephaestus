package de.tum.cit.aet.hephaestus.practices.review.autonomy;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.Nullable;

public final class AutonomyResolver {

    private AutonomyResolver() {}

    public static PracticeAutonomy workspaceDefault(@Nullable PracticeAutonomy workspaceOverride) {
        return workspaceOverride != null ? workspaceOverride : PracticeAutonomy.DEFAULT;
    }

    public static EffectiveAutonomy resolveArea(
        @Nullable PracticeAutonomy areaAutonomy,
        PracticeAutonomy workspaceDefault
    ) {
        return areaAutonomy != null
            ? new EffectiveAutonomy(areaAutonomy, AutonomySource.AREA)
            : new EffectiveAutonomy(workspaceDefault, AutonomySource.WORKSPACE);
    }

    public static EffectiveAutonomy resolveArea(@Nullable PracticeArea area, PracticeAutonomy workspaceDefault) {
        return resolveArea(area == null ? null : area.getAutonomy(), workspaceDefault);
    }

    public static EffectiveAutonomy resolvePractice(
        @Nullable PracticeAutonomy practiceAutonomy,
        @Nullable PracticeAutonomy areaAutonomy,
        PracticeAutonomy workspaceDefault
    ) {
        if (practiceAutonomy != null) {
            return new EffectiveAutonomy(practiceAutonomy, AutonomySource.PRACTICE);
        }
        return resolveArea(areaAutonomy, workspaceDefault);
    }

    public static EffectiveAutonomy resolvePractice(Practice practice, PracticeAutonomy workspaceDefault) {
        PracticeArea area = practice.getArea();
        return resolvePractice(practice.getAutonomy(), area == null ? null : area.getAutonomy(), workspaceDefault);
    }

    public static PracticeAutonomy effectiveAutonomyOf(Practice practice, PracticeAutonomy workspaceDefault) {
        return resolvePractice(practice, workspaceDefault).autonomy();
    }
}
