package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Service Provider Interface for checking agent configuration state.
 * <p>
 * This abstraction decouples the practices module from the agent module,
 * preventing a cyclic dependency: practices → agent → practices.
 */
public interface AgentConfigChecker {
    /**
     * Checks whether at least one enabled agent configuration exists for the given workspace.
     *
     * @param workspaceId the workspace ID to check
     * @return true if an enabled agent config exists, false otherwise
     */
    boolean hasEnabledConfig(Long workspaceId);

    /**
     * Checks whether practice detection has a config that will <em>actually run</em>, honouring the
     * workspace's {@code practiceConfigId} binding. When a config is bound, that specific config must
     * exist and be enabled (a bound-but-disabled binding pauses detection); when unbound, any enabled
     * config qualifies. This mirrors {@code AgentJobService.resolvePracticeConfigs} so the gate never
     * lets detection run (incurring LLM cost) only for submission to resolve to zero jobs.
     *
     * @param workspaceId   the workspace ID to check
     * @param boundConfigId the workspace's practiceConfigId binding, or {@code null} when unbound
     * @return true iff at least one config would be submitted for practice detection
     */
    boolean hasRunnablePracticeConfig(Long workspaceId, Long boundConfigId);
}
