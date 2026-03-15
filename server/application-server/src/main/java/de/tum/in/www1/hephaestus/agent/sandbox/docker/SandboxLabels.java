package de.tum.in.www1.hephaestus.agent.sandbox.docker;

/**
 * Docker label constants for Hephaestus-managed sandbox containers.
 * Used for lifecycle management and reconciliation.
 */
final class SandboxLabels {

    static final String MANAGED = "hephaestus.managed";
    static final String JOB_ID = "hephaestus.job-id";

    private SandboxLabels() {}
}
