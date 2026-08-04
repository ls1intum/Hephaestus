package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Why a run that reached {@link AgentJobStatus#COMPLETED} produced the findings it did.
 *
 * <p>A run that declines for want of evidence completes successfully — nothing failed — so status
 * alone cannot separate it from a run that looked and found nothing. Without that separation a
 * refusal reads to a developer as a clean bill of health, which is the one reading an abstention
 * exists to prevent.
 */
public enum ReviewRunOutcome {
    /** The model ran against sufficient evidence. Absence of findings means none were found. */
    JUDGED,
    /**
     * Required evidence was missing, unreadable, stale, or unauthorized, so no model ran. Absence of
     * findings means nothing was assessed.
     */
    INSUFFICIENT_EVIDENCE;

    static final String OUTPUT_FIELD = "outcome";

    /** Reads the outcome an executor recorded on {@code agent_job.output}; defaults to {@link #JUDGED}. */
    static ReviewRunOutcome fromJobOutput(tools.jackson.databind.JsonNode output) {
        if (output == null || !output.has(OUTPUT_FIELD)) {
            return JUDGED;
        }
        String value = output.get(OUTPUT_FIELD).asString(null);
        return INSUFFICIENT_EVIDENCE.name().equals(value) ? INSUFFICIENT_EVIDENCE : JUDGED;
    }
}
