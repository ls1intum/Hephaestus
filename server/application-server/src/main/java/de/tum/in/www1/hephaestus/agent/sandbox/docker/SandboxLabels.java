package de.tum.in.www1.hephaestus.agent.sandbox.docker;

/**
 * Docker label constants for Hephaestus-managed sandbox containers. Used for lifecycle management
 * and reconciliation.
 *
 * <p>Visible across the {@code agent.sandbox.docker.*} package tree (sync + interactive subpackage)
 * so that the reconciler can sweep orphans from both flows by the shared {@link #MANAGED} label.
 */
public final class SandboxLabels {

    public static final String MANAGED = "hephaestus.managed";
    public static final String JOB_ID = "hephaestus.job-id";

    /** Distinguishes one-shot sync agents from long-lived interactive sandboxes. */
    public static final String KIND = "hephaestus.kind";

    public static final String KIND_SYNC = "sync";
    public static final String KIND_INTERACTIVE = "interactive";

    /** Session identifier for interactive sandboxes (analogous to {@link #JOB_ID} for sync). */
    public static final String SESSION_ID = "hephaestus.session-id";

    private SandboxLabels() {}
}
