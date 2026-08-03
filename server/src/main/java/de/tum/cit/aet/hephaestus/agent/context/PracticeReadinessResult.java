package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.PracticeReadinessDecision;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Objects;

public record PracticeReadinessResult(List<Practice> readyPractices, List<PracticeReadinessDecision> decisions) {
    public PracticeReadinessResult {
        readyPractices = List.copyOf(Objects.requireNonNull(readyPractices, "readyPractices"));
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }
}
