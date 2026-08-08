package de.tum.cit.aet.hephaestus.agent.runtime;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared constants for the Pi sandbox workspace ABI documented at
 * {@code docs/contributor/agent/workspace-abi.mdx}. Avoid duplicating these literals —
 * they are the wire contract between the server-side workspace materialiser and the in-container
 * runner.
 */
public final class SandboxLayout {

    private SandboxLayout() {}

    /** Container workspace root. */
    public static final String WORKSPACE_ROOT = "/workspace";

    // ── Layout (ADR 0020): read-only vs writable by LOCATION, not lore ──────────────────────────────
    //   inputs/  — EVERYTHING the agent may only read (the path-guard whitelists exactly this subtree)
    //   work/    — scratch the agent + precompute write during the run; NEVER collected
    //   out/     — the ONLY directory collected back into SQL
    //   .pi/     — the Pi SDK runtime home (vendor dir)

    /** Workspace-relative prefix for the read-only input subtree (the only region the path-guard whitelists). */
    public static final String INPUTS_PREFIX = "inputs/";

    public static final String SOURCES_PREFIX = INPUTS_PREFIX + "sources/";

    public static String sourceMount(String originId) {
        return SOURCES_PREFIX + originId + "/";
    }

    public static final String SCM_SOURCE_KEEP = sourceMount("scm") + ".keep";

    public static final String REPO_MOUNT = WORKSPACE_ROOT + "/" + sourceMount("scm") + "repo";

    /** Workspace-relative prefix the agent cites for repo files ({@code inputs/sources/scm/repo/<path>}). */
    public static final String REPO_MOUNT_RELATIVE = sourceMount("scm") + "repo/";

    /** Output directory the sandbox collects after the run. */
    public static final String OUTPUT_PATH = WORKSPACE_ROOT + "/out";

    /** Workspace-relative filename of the task envelope ({@code task.json}). */
    public static final String TASK_ENVELOPE_FILENAME = "task.json";

    /** Workspace-relative prefix every {@link de.tum.cit.aet.hephaestus.agent.context.ContentSource} must write under. */
    public static final String CONTEXT_PREFIX = INPUTS_PREFIX + "context/";

    public static final String MANIFEST_PATH = INPUTS_PREFIX + "manifest.json";

    /**
     * Workspace-relative prefix for what earlier reviews recorded and already said.
     *
     * <p>Separate from {@link #CONTEXT_PREFIX} because it is the one part of the sandbox that is not
     * about the artifact under review: {@code inputs/context/} is this event, {@code inputs/history/} is
     * every event before it. Both files below it are always present — an empty one is the review saying
     * it looked and there was nothing, which is a different fact from never having looked.
     */
    public static final String HISTORY_PREFIX = INPUTS_PREFIX + "history/";

    /** Workspace-relative prefix for per-practice catalog files (index, criteria). */
    public static final String PRACTICES_PREFIX = INPUTS_PREFIX + "practices/";

    /**
     * Workspace-relative prefix for the writable scratch region (ADR 0020). Everything the agent +
     * precompute write during a run lives here; the sandbox makes this subtree writable by the
     * container uid while {@link #INPUTS_PREFIX} stays read-only. Never collected back into SQL.
     */
    public static final String WORK_PREFIX = "work/";

    /** Workspace-relative prefix for per-practice precompute scripts injected from the database. */
    public static final String PRECOMPUTE_PREFIX = WORK_PREFIX + "precompute/";

    /** Workspace-relative prefix for runtime precompute output (logs, structured hints). */
    public static final String PRECOMPUTE_OUT_PREFIX = WORK_PREFIX + "precompute-out/";

    /** Workspace-relative prefix for analysis markers ({@link #ANALYSIS_PRACTICES_PREFIX} is a child). */
    public static final String ANALYSIS_PREFIX = WORK_PREFIX + "analysis/";

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

    /**
     * Workspace-relative filename of the non-secret LLM-provider spec written by
     * {@link PiRuntimeFactory#buildProviderConfigJson}: wire protocol, upstream model id, capability
     * envelope. The proxy URL is not in it — that arrives as {@code $LLM_PROXY_URL}.
     */
    public static final String PROVIDER_CONFIG_FILENAME = "pi-provider.json";

    /** Workspace-relative filename of the shared provider-registration ES module both runners import. */
    public static final String PROVIDER_HELPER_FILENAME = "pi-provider.mjs";

    /** Workspace-relative filename of the orchestrator instructions loaded into Pi at runtime. */
    public static final String ORCHESTRATOR_FILENAME = "AGENTS.md";

    /** Workspace-relative path of the orchestrator instructions (combination of prefix + filename). */
    public static final String ORCHESTRATOR_PATH = PI_AGENT_PREFIX + ORCHESTRATOR_FILENAME;

    /** Mentor system prompt path — pinned by {@code SandboxLayoutSyncTest} against pi-mentor-runner.mjs. */
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
        return Set.of(CONTEXT_PREFIX, SESSIONS_DIR_PREFIX);
    }
}
