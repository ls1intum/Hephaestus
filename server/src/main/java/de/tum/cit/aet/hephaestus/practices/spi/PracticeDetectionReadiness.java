package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Service Provider Interface answering whether a workspace can actually run practice detection.
 * <p>
 * This abstraction decouples the practices module from the agent module,
 * preventing a cyclic dependency: practices → agent → practices.
 */
public interface PracticeDetectionReadiness {
    /**
     * Checks whether practice detection will <em>actually run</em> for a workspace: it has an enabled
     * {@code PRACTICE_DETECTION} agent binding whose model is available now. Mirrors what job
     * submission resolves, so the gate never lets detection run (incurring LLM cost) only for
     * submission to produce zero jobs.
     *
     * @param workspaceId the workspace ID to check
     * @return true iff a practice-detection job would be submitted
     */
    boolean hasRunnableAgent(Long workspaceId);
}
