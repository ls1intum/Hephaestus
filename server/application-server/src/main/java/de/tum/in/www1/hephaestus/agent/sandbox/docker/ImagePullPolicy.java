package de.tum.in.www1.hephaestus.agent.sandbox.docker;

/** Mirrors Kubernetes {@code imagePullPolicy}: ALWAYS, IF_NOT_PRESENT, NEVER. */
public enum ImagePullPolicy {
    ALWAYS,
    IF_NOT_PRESENT,
    NEVER
}
