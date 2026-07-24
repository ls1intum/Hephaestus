package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Service Provider Interface for checking agent configuration state.
 * <p>
 * This abstraction decouples the practices module from the agent module,
 * preventing a cyclic dependency: practices → agent → practices.
 */
public interface AgentConfigChecker {
    /**
     * Checks whether practice detection will <em>actually run</em> for a workspace: it has an enabled
     * {@code PRACTICE_DETECTION} binding whose model is available now. Mirrors what
     * {@code AgentJobService.submit} resolves, so the gate never lets detection run (incurring LLM
     * cost) only for submission to produce zero jobs.
     *
     * @param workspaceId the workspace ID to check
     * @return true iff a practice-detection job would be submitted
     */
    boolean hasRunnablePractice(Long workspaceId);
}
