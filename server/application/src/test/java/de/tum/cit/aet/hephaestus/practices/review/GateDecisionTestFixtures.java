package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;

public final class GateDecisionTestFixtures {

    private GateDecisionTestFixtures() {}

    public static GateDecision.Detect automaticDetection(Workspace workspace, List<Practice> practices) {
        return new GateDecision.Detect(
                workspace, practices, workspace.getReviewSettings().getRolloutRevision(), TriggerMode.AUTO);
    }
}
