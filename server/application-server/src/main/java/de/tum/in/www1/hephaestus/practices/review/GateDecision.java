package de.tum.in.www1.hephaestus.practices.review;

import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Objects;

/**
 * Result of the practice review detection gate evaluation.
 * <p>
 * Uses a sealed interface so callers must handle both outcomes, and each
 * variant carries only the data relevant to that outcome:
 * <ul>
 *   <li>{@link Detect}: the gate passed — carries the resolved workspace
 *       and matched practices so downstream consumers don't need to re-query</li>
 *   <li>{@link Skip}: the gate rejected — carries a human-readable reason
 *       for diagnostics and logging</li>
 * </ul>
 */
public sealed interface GateDecision permits GateDecision.Detect, GateDecision.Skip {
    /**
     * The gate passed: the practice review agent should run.
     *
     * @param workspace         the resolved workspace for this PR's repository
     * @param matchedPractices  active practices whose trigger events match the current event
     */
    record Detect(Workspace workspace, List<Practice> matchedPractices) implements GateDecision {
        public Detect {
            Objects.requireNonNull(workspace, "workspace must not be null");
            matchedPractices = List.copyOf(matchedPractices);
        }
    }

    /**
     * The gate rejected: the practice review agent should NOT run.
     *
     * @param reason a short, human-readable reason for the skip (for logging/diagnostics)
     */
    record Skip(String reason) implements GateDecision {}
}
