package de.tum.cit.aet.hephaestus.agent.runtime;

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

    /**
     * Workspace-relative prefix for entitled connector bulk artifacts (ADR 0020): the SCM clone is
     * {@code blobs/scm/repo}, a future Slack/Outline export is {@code blobs/slack/...} — the repo is one
     * mount among peers, not a privileged root.
     */
    public static final String BLOBS_PREFIX = "blobs/";

    /** Workspace-relative {@code .keep} that pre-creates {@code blobs/scm/} so the repo can mount under it. */
    public static final String SCM_BLOB_KEEP = BLOBS_PREFIX + "scm/.keep";

    /** Bind-mount point for the read-only git checkout — the SCM connector's bulk artifact. */
    public static final String REPO_MOUNT = WORKSPACE_ROOT + "/" + BLOBS_PREFIX + "scm/repo";

    /** Workspace-relative name of the back-compat repo symlink ({@code repo} → {@link #REPO_SYMLINK_TARGET}). */
    public static final String REPO_SYMLINK_LINK = "repo";

    /** Relative target of the {@code repo} back-compat symlink, so {@code repo/} keeps resolving. */
    public static final String REPO_SYMLINK_TARGET = BLOBS_PREFIX + "scm/repo";

    /** Output directory the sandbox collects after the run. */
    public static final String OUTPUT_PATH = WORKSPACE_ROOT + "/.output";

    /** Workspace-relative filename of the task envelope ({@code task.json}). */
    public static final String TASK_ENVELOPE_FILENAME = "task.json";

    /** Workspace-relative prefix every {@link de.tum.cit.aet.hephaestus.agent.context.ContentProvider} must write under. */
    public static final String CONTEXT_TARGET_PREFIX = "context/target/";

    /**
     * Workspace-relative path of the integration-agnostic context manifest (the "telescope"): a small
     * index of every projected context file with its connector + provenance, so the agent — and a future
     * connector — sees one uniform entry point regardless of which integration produced the bytes.
     */
    public static final String MANIFEST_PATH = CONTEXT_TARGET_PREFIX + "manifest.json";

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
