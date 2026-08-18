package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessDecision;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;

public record AutomatedReviewReadinessResult(
    List<Practice> readyPractices,
    List<AutomatedReviewReadinessDecision> decisions
) {
    public AutomatedReviewReadinessResult {
        readyPractices = List.copyOf(Objects.requireNonNull(readyPractices, "readyPractices"));
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }
}
