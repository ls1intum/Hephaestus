package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
     * @param matchedPractices practices used in new reviews whose trigger events match the current event
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
     * @param signalReason the controlled-vocabulary reason to record the refused signal under, or
     *     {@code null} to let the caller keep its default of {@link SignalStateReason#GATE_SKIPPED}. Most
     *     skips are diagnostic detail on one class of answer and stay null; a skip names a reason here
     *     only when the class of answer itself differs — a practice silenced to {@code OFF} is a
     *     different fact from "the gate declined", and only one of them is lifted by an admin.
     */
    record Skip(String reason, @Nullable SignalStateReason signalReason) implements GateDecision {
        /** A skip that carries only diagnostic prose; the signal is recorded as {@code GATE_SKIPPED}. */
        public Skip(String reason) {
            this(reason, null);
        }

        /** The reason to record this refusal under, resolving the unnamed case to {@code GATE_SKIPPED}. */
        public SignalStateReason resolvedSignalReason() {
            return signalReason != null ? signalReason : SignalStateReason.GATE_SKIPPED;
        }
    }
}
