package de.tum.in.www1.hephaestus.agent.sandbox;

/**
 * Mirrors Kubernetes {@code imagePullPolicy}: ALWAYS, IF_NOT_PRESENT, NEVER.
 *
 * <p>Lives in {@code agent.sandbox} (not {@code agent.sandbox.docker}) because it's a public
 * config value type referenced by agent properties classes outside the sandbox module.
 * The Docker-internal-only rule pins the implementation but exempts this shared enum.
 */
public enum ImagePullPolicy {
    ALWAYS,
    IF_NOT_PRESENT,
    NEVER,
}
