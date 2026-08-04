package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.AutomatedAssessmentReadinessDecision;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;

public record AutomatedAssessmentReadinessResult(
    List<Practice> readyPractices,
    List<AutomatedAssessmentReadinessDecision> decisions
) {
    public AutomatedAssessmentReadinessResult {
        readyPractices = List.copyOf(Objects.requireNonNull(readyPractices, "readyPractices"));
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }
}
