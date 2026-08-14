package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Why a run that reached {@link AgentJobStatus#COMPLETED} produced the observations it did.
 *
 * <p>A run that skips automated review for insufficient evidence completes successfully, because
 * nothing failed. Status alone therefore cannot distinguish it from a run that assessed the work and
 * produced no observations. Without that distinction a skipped review is indistinguishable from a clean
 * result, which is precisely the reading an abstention exists to prevent.
 */
public enum ReviewRunOutcome {
    /** Automated review ran against sufficient evidence; no observations means none were identified. */
    REVIEWED,
    /**
     * Required evidence was missing, unreadable, out of date, or unauthorized, so no model ran and no
     * practice was assessed.
     */
    INSUFFICIENT_EVIDENCE;

    static final String OUTPUT_FIELD = "outcome";

    /** Reads the outcome an executor recorded on {@code agent_job.output}; defaults to {@link #REVIEWED}. */
    static ReviewRunOutcome fromJobOutput(tools.jackson.databind.JsonNode output) {
        if (output == null || !output.has(OUTPUT_FIELD)) {
            return REVIEWED;
        }
        String value = output.get(OUTPUT_FIELD).asString(null);
        return INSUFFICIENT_EVIDENCE.name().equals(value) ? INSUFFICIENT_EVIDENCE : REVIEWED;
    }
}
