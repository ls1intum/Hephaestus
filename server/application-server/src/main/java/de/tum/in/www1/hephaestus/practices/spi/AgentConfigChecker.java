package de.tum.in.www1.hephaestus.practices.spi;

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
}
