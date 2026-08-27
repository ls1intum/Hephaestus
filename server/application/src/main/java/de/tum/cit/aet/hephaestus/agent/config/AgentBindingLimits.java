package de.tum.cit.aet.hephaestus.agent.config;

/**
 * The range a workspace administrator may set a binding's per-run timeout to. The ceiling bounds how
 * long any single agent run may last, so anything that must outlive a run derives its window from it.
 */
public final class AgentBindingLimits {

    public static final int MIN_TIMEOUT_SECONDS = 30;

    public static final int MAX_TIMEOUT_SECONDS = 10800;

    private AgentBindingLimits() {}
}
