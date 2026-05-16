package de.tum.in.www1.hephaestus.agent.runtime;

import java.util.regex.Pattern;

/**
 * Shared constants for the Pi sandbox workspace ABI documented at
 * {@code docs/contributor/agent/workspace-abi.mdx}. Avoid duplicating these literals —
 * they are the wire contract between the server-side workspace materialiser and the in-container
 * runner.
 */
public final class WorkspaceAbi {

    private WorkspaceAbi() {}

    /** Container workspace root. */
    public static final String WORKSPACE_ROOT = "/workspace";

    /** Bind-mount point for the read-only git repository checkout. */
    public static final String REPO_MOUNT = WORKSPACE_ROOT + "/repo";

    /** Output directory the sandbox collects after the run. */
    public static final String OUTPUT_PATH = WORKSPACE_ROOT + "/.output";

    /** Workspace-relative filename of the task envelope ({@code task.json}). */
    public static final String TASK_ENVELOPE_FILENAME = "task.json";

    /** Workspace-relative prefix every {@link de.tum.in.www1.hephaestus.agent.context.ContentProvider} must write under. */
    public static final String CONTEXT_TARGET_PREFIX = "context/target/";

    /** Workspace-relative prefix for per-practice catalog files (index, criteria). */
    public static final String PRACTICES_PREFIX = ".practices/";

    /** Workspace-relative prefix for per-practice precompute scripts injected from the database. */
    public static final String PRECOMPUTE_PREFIX = ".precompute/";

    /** Workspace-relative prefix for runtime precompute output (logs, structured hints). */
    public static final String PRECOMPUTE_OUT_PREFIX = ".precompute-out/";

    /** Workspace-relative prefix for analysis markers ({@link #ANALYSIS_PRACTICES_PREFIX} is a child). */
    public static final String ANALYSIS_PREFIX = ".analysis/";

    /** Workspace-relative path of the practices-analysis marker directory. */
    public static final String ANALYSIS_PRACTICES_PREFIX = ANALYSIS_PREFIX + "practices/";

    /** Workspace-relative path the Pi runtime config (settings.json) is staged into. */
    public static final String PI_RUNTIME_PREFIX = ".pi-runtime/";

    /** Workspace-relative path the orchestrator instructions ({@code .pi/AGENTS.md}) live under. */
    public static final String PI_AGENT_PREFIX = ".pi/";

    /** Workspace-relative filename of the runner script copied from the classpath. */
    public static final String RUNNER_SCRIPT_FILENAME = ".run-pi.mjs";

    /** Workspace-relative filename of the orchestrator instructions loaded into Pi at runtime. */
    public static final String ORCHESTRATOR_FILENAME = "AGENTS.md";

    /** Workspace-relative path of the orchestrator instructions (combination of prefix + filename). */
    public static final String ORCHESTRATOR_PATH = PI_AGENT_PREFIX + ORCHESTRATOR_FILENAME;

    /**
     * Workspace-relative path of the mentor runner's system prompt file. Single-sourced so the
     * Java materialiser (writing the bytes) and the JavaScript runner (reading them) cannot drift.
     */
    public static final String MENTOR_SYSTEM_PROMPT_PATH = "agent/mentor/system.md";

    /** Exit code emitted by the Pi runner on envelope/image drift (unsupported {@code schemaVersion} or {@code kind}). */
    public static final int EXIT_ENVELOPE_MISMATCH = 42;

    /** Practice slug pattern enforced at the handler boundary as defense-in-depth against FS-path injection. */
    public static final Pattern PRACTICE_SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
}
