package de.tum.in.www1.hephaestus.agent.runtime;

import java.util.Set;
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

    /** Workspace-relative directory name of the Pi SDK agent dir. */
    private static final String PI_AGENT_NAME = ".pi";

    /** Workspace-relative path of the Pi SDK agent dir — settings.json, AGENTS.md, extensions/. */
    public static final String PI_AGENT_PREFIX = PI_AGENT_NAME + "/";

    /** Absolute container path of the Pi SDK agent dir — value of {@code PI_CODING_AGENT_DIR}. */
    public static final String PI_AGENT_DIR = WORKSPACE_ROOT + "/" + PI_AGENT_NAME;

    /** Workspace-relative filename of the runner script copied from the classpath. */
    public static final String RUNNER_SCRIPT_FILENAME = ".run-pi.mjs";

    /** Workspace-relative filename of the orchestrator instructions loaded into Pi at runtime. */
    public static final String ORCHESTRATOR_FILENAME = "AGENTS.md";

    /** Workspace-relative path of the orchestrator instructions (combination of prefix + filename). */
    public static final String ORCHESTRATOR_PATH = PI_AGENT_PREFIX + ORCHESTRATOR_FILENAME;

    /** Mentor system prompt path — pinned by {@code WorkspaceAbiSyncTest} against pi-mentor-runner.mjs. */
    public static final String MENTOR_SYSTEM_PROMPT_PATH = "agent/mentor/system.md";

    /** Workspace-relative directory for restored Pi SDK session JSONL files (matches the mentor runner's {@code SESSIONS_DIR}). */
    public static final String SESSIONS_DIR_PREFIX = ".sessions/";

    /** Exit code emitted by the Pi runner on envelope/image drift (unsupported {@code schemaVersion} or {@code kind}). */
    public static final int EXIT_ENVELOPE_MISMATCH = 42;

    /** Practice slug pattern enforced at the handler boundary as defense-in-depth against FS-path injection. */
    public static final Pattern PRACTICE_SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    /** Exact workspace paths an adapter may pass in {@link PiPlanSpec#extraInputs()}. */
    public static Set<String> allowedExtraInputPaths() {
        return Set.of(MENTOR_SYSTEM_PROMPT_PATH);
    }

    /** Workspace-path prefixes accepted by {@link PiPlanSpec#extraInputs()} for dynamic-suffix paths. */
    public static Set<String> allowedExtraInputPrefixes() {
        return Set.of(CONTEXT_TARGET_PREFIX, SESSIONS_DIR_PREFIX);
    }
}
