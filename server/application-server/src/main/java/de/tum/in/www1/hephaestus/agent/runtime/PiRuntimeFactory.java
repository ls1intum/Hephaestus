package de.tum.in.www1.hephaestus.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Shared Pi-agent kernel. Builds the sandbox-level scaffolding (settings, auth, env, workspace
 * inputs, base command, classpath resources, network policy) that every Pi-based agent reuses.
 *
 * <p>Stays domain-agnostic: callers supply a {@link PiPlanSpec} (provider, credentials, runner
 * filename, precompute step). Nothing here knows about practices or chat sessions.
 */
@Component
public class PiRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(PiRuntimeFactory.class);

    /** Default workspace output directory inside the container — see {@link WorkspaceAbi#OUTPUT_PATH}. */
    public static final String OUTPUT_PATH = WorkspaceAbi.OUTPUT_PATH;

    /** Reserved for retries + container cleanup before the sandbox kills the runner. */
    public static final int TIMEOUT_BUFFER_SECONDS = 60;

    /** Classpath prefix for all Pi-related resources ({@code pi-runner.mjs}, {@code pi-orchestrator.md}). */
    static final String AGENT_RESOURCE_PREFIX = "agent/";

    private final ObjectMapper objectMapper;

    public PiRuntimeFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Build a Pi sandbox plan ready for the executor. */
    public PiPlan build(PiPlanSpec spec) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        String authSetup = LlmProxyAuthShell.build(
            spec.credentialMode(),
            spec.provider(),
            spec.credential(),
            spec.baseUrl(),
            spec.modelName(),
            env
        );

        // The custom-provider extension is only emitted when the caller pinned a baseUrl on a
        // non-Azure provider in API_KEY/OAUTH mode (production keeps baseUrl null → built-in
        // provider; live tests set it → custom hephaestus provider). PROXY mode never needs the
        // extension because the proxy is already on the standard OPENAI_BASE_URL.
        boolean useCustomProvider = shouldRegisterHephaestusProvider(spec);
        if (useCustomProvider) {
            inputFiles.put(
                WorkspaceAbi.PI_AGENT_PREFIX + "extensions/hephaestus-provider.ts",
                buildExtensionFile(spec)
            );
        }

        inputFiles.put(
            WorkspaceAbi.PI_AGENT_PREFIX + "settings.json",
            buildPiSettingsJson(spec.provider(), spec.modelName(), useCustomProvider)
        );
        inputFiles.put(WorkspaceAbi.ORCHESTRATOR_PATH, loadClasspathResource("pi-orchestrator.md"));
        inputFiles.put(WorkspaceAbi.RUNNER_SCRIPT_FILENAME, loadClasspathResource(spec.runnerScript()));
        inputFiles.putAll(spec.extraInputs());

        long agentTimeoutMs = Math.max(60_000L, (long) (spec.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        env.put("AGENT_BUDGET_MS", Long.toString(agentTimeoutMs));

        // TMPDIR resolves under the mandatory /home/agent/.local tmpfs (see ContainerSecurityPolicy);
        // Pi runs as uid 1000 and the Dockerfile pre-creates and chowns /home/agent so $HOME writes work.
        env.put("HOME", "/home/agent");
        env.put("XDG_CONFIG_HOME", "/home/agent/.config");
        env.put("TMPDIR", "/home/agent/.local/tmp");
        env.put("PI_CODING_AGENT_DIR", WorkspaceAbi.PI_AGENT_DIR);

        // Pi's azure-openai-responses provider hard-defaults the model to "gpt-5.2" — the deployment
        // map routes both that and the configured model to the correct Azure deployment.
        if (spec.provider() == LlmProvider.AZURE_OPENAI) {
            String deployment = (spec.modelName() != null && !spec.modelName().isBlank())
                ? spec.modelName()
                : "gpt-5.4-mini";
            // Model name flows to the shell via the env map (not interpolated into a shell string),
            // so docker-java handles the quoting. The map *value* is then read by the agent.
            env.put("AZURE_OPENAI_DEPLOYMENT_NAME_MAP", deployment + "=" + deployment + ",gpt-5.2=" + deployment);
        }

        String workspaceRoot = WorkspaceAbi.WORKSPACE_ROOT;
        String command =
            authSetup +
            "mkdir -p " +
            WorkspaceAbi.OUTPUT_PATH +
            " /home/agent/.config /home/agent/.local/tmp && " +
            // Pi SDK ESM imports require a workspace-local node_modules.
            "ln -sf /usr/local/lib/node_modules " +
            workspaceRoot +
            "/node_modules && " +
            spec.precomputeStep() +
            // Per-agent Node flags + envs. We deliberately do NOT apply the same flags to both
            // the long-lived mentor runner and the one-shot practice runner — see the rationale
            // in {@link #nodeFlagsFor(String)} and {@link #nodeEnvFor(String)}.
            nodeEnvFor(spec.runnerScript()) +
            "node " +
            nodeFlagsFor(spec.runnerScript()) +
            workspaceRoot +
            "/" +
            WorkspaceAbi.RUNNER_SCRIPT_FILENAME;

        NetworkPolicy networkPolicy = buildNetworkPolicy(
            spec.credentialMode(),
            spec.provider(),
            spec.jobToken(),
            spec.allowInternet()
        );

        log.debug(
            "Built Pi plan: timeout={}s, provider={}, credentialMode={}, files={}",
            spec.timeoutSeconds(),
            spec.provider(),
            spec.credentialMode(),
            inputFiles.size()
        );
        return new PiPlan(List.of("sh", "-c", command), Map.copyOf(env), Map.copyOf(inputFiles), networkPolicy);
    }

    /** Mentor uses a long-lived runner; the script filename is the dispatch key. */
    static final String MENTOR_RUNNER_SCRIPT = "pi-mentor-runner.mjs";

    /**
     * Per-runner Node CLI flags. We split mentor (long-lived JSONL pump, 50+ concurrent containers
     * on a single host) from practice (one-shot review, reads large diffs, runs precompute).
     *
     * <p><b>Mentor flags:</b>
     * <ul>
     *   <li>{@code --max-old-space-size=256} — cap V8 old-gen at 256 MB. Empirically the mentor
     *       runtime sits at ~100 MB V8 heap; 2.5× headroom defends against a leaky session OOM-ing
     *       the host instead of itself. Default ~1.4 GB on 64-bit lets one bad runner take the
     *       host down.</li>
     *   <li>{@code --no-warnings} — keeps stderr clean for ops grep against our own log prefix.</li>
     *   <li>{@code --expose-gc} — exposes {@code global.gc()} so the runner can force a post-turn
     *       compaction in {@code pi-mentor-runner.mjs:forwardEvent}. The flag costs nothing on its
     *       own and is only effective when {@code global.gc()} is actually called.</li>
     * </ul>
     *
     * <p><b>Practice flags:</b> only {@code --no-warnings}. We do NOT cap the heap because practice
     * routinely parses 30-file diff patches that allocate transiently; a 256 MB cap would convert
     * worst-case-input OOMs from "rare" to "regular." We do NOT {@code --expose-gc} because the
     * practice runner never calls {@code global.gc()} and exposing the global is a foot-gun.
     *
     * <p>Note: {@code --disable-source-maps} was removed — the flag was dropped in Node 22 (source
     * maps are off by default; the flag itself no longer exists and causes {@code bad option} exit 9).
     *
     * <p>We deliberately removed {@code --max-semi-space-size=16} and {@code UV_THREADPOOL_SIZE=2}
     * from prior revisions: the former matches the Node 22 default on 64-bit (so it was a no-op),
     * and the latter risks serialising libuv fs/crypto bursts (notably the practice runner's
     * git tool calls) for an unmeasured ~MB-scale RSS reservation reduction.
     */
    private static String nodeFlagsFor(String runnerScript) {
        if (MENTOR_RUNNER_SCRIPT.equals(runnerScript)) {
            return "--max-old-space-size=256 --no-warnings --expose-gc ";
        }
        return "--no-warnings ";
    }

    /**
     * Per-runner environment fragments injected as {@code VAR=value} pairs into the shell
     * {@code node …} invocation. This scopes {@code LD_PRELOAD=libjemalloc.so.2} +
     * {@code MALLOC_CONF=…} to the Node process only — NOT image-wide ENV — so the precompute
     * runner ({@code bun}), {@code git}, {@code jq}, and short-lived tool invocations all keep
     * their default glibc allocators. Mentor's long-lived heap benefits from jemalloc's
     * page-decay tuning; precompute's bursty allocations don't.
     *
     * <p>The path matches the {@code /usr/local/lib/libjemalloc.so.2} symlink created by the Pi
     * Dockerfile (see {@code docker/agents/pi/Dockerfile}). The symlink is per-arch by design;
     * the env literal here is arch-independent.
     */
    private static String nodeEnvFor(String runnerScript) {
        if (MENTOR_RUNNER_SCRIPT.equals(runnerScript)) {
            // `background_thread:true` runs jemalloc's page-decay sweep on a dedicated thread —
            // without it the mutator must re-enter the allocator to trigger decay, which a
            // long-idle Node loop rarely does (cf. jemalloc TUNING.md). Decay window 30s
            // matches jemalloc upstream's "long-lived process" recommendation; 10s was
            // aggressive without buying anything once background_thread is on.
            return (
                "LD_PRELOAD=/usr/local/lib/libjemalloc.so.2 " +
                "MALLOC_CONF=background_thread:true,narenas:2,dirty_decay_ms:30000,muzzy_decay_ms:30000 "
            );
        }
        return "";
    }

    /**
     * Map the Hephaestus {@link LlmProvider} enum to its Pi provider token.
     * Identical across all known agent roles today; when mentor introduces different
     * provider mappings, extract this into a strategy.
     */
    static String providerToken(LlmProvider provider) {
        return switch (provider) {
            case AZURE_OPENAI -> "azure-openai-responses";
            case OPENAI -> "openai";
            case ANTHROPIC -> "anthropic";
        };
    }

    /**
     * Build settings JSON shared by every Pi-based agent (practice review and mentor chat alike).
     * Two-arg overload kept for the test surface that doesn't care about custom provider routing.
     */
    byte[] buildPiSettingsJson(LlmProvider provider, @Nullable String modelName) {
        return buildPiSettingsJson(provider, modelName, false);
    }

    /**
     * When {@code useCustomProvider} is true, {@code defaultProvider} routes to the
     * hephaestus extension (see {@link #buildExtensionFile}). The {@code defaultModel} is then
     * the configured model id — the extension is registered with exactly that model.
     *
     * <p>Public so live tests in {@code agent.mentor.live} can reuse the production bytes
     * verbatim instead of duplicating the JSON shape.
     */
    public byte[] buildPiSettingsJson(LlmProvider provider, @Nullable String modelName, boolean useCustomProvider) {
        Map<String, Object> settings = new LinkedHashMap<>();
        if (useCustomProvider) {
            settings.put("defaultProvider", "hephaestus");
        } else {
            settings.put("defaultProvider", providerToken(provider));
        }
        if (modelName != null && !modelName.isBlank()) {
            settings.put("defaultModel", modelName);
        }
        settings.put("transport", "sse");
        Map<String, Object> compaction = new LinkedHashMap<>();
        compaction.put("enabled", true);
        compaction.put("reserveTokens", 16384);
        settings.put("compaction", compaction);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Pi practice settings", e);
        }
    }

    /**
     * True when the spec needs a custom Pi provider extension: only for non-Azure providers in
     * API_KEY/OAUTH mode with a non-blank {@code baseUrl}. PROXY mode and Azure both have their
     * own routing primitives (proxy URL injected at runtime; Azure deployment-name map).
     */
    static boolean shouldRegisterHephaestusProvider(PiPlanSpec spec) {
        if (spec.credentialMode() == CredentialMode.PROXY) return false;
        if (spec.provider() == LlmProvider.AZURE_OPENAI) return false;
        return spec.baseUrl() != null && !spec.baseUrl().isBlank();
    }

    /**
     * Emit a Pi extension that registers a custom provider named {@code hephaestus}. The
     * provider reads its base URL, API key, and model id from the env (set by
     * {@link LlmProxyAuthShell}). Pi auto-discovers extensions in {@code ~/.pi/extensions/} via
     * jiti at session start — no TypeScript compile step needed.
     *
     * <p>{@code api: "openai-completions"} routes to Pi's chat-completions implementation for
     * OpenAI; {@code anthropic-messages} routes to the Anthropic Messages API. {@code authHeader:
     * true} attaches {@code Authorization: Bearer $PI_HEPHAESTUS_API_KEY}.
     */
    public byte[] buildExtensionFile(PiPlanSpec spec) {
        // The TS source lives at server/application-server/src/main/resources/agent/extensions/.
        // It is typechecked at CI time against @earendil-works/pi-coding-agent@0.74.0 via the
        // sibling npm workspace `agent-extensions` (npm -w server/application-server/agent-extensions
        // run typecheck). Bump in lockstep with MentorLiveLlmTest.PI_SDK_VERSION.
        String resource =
            spec.provider() == LlmProvider.ANTHROPIC
                ? "extensions/provider-anthropic.ts"
                : "extensions/provider-openai.ts";
        return loadClasspathResource(resource);
    }

    /**
     * Network policy: PROXY mode forwards {@code allowInternet} + {@code jobToken}; direct modes
     * always allow internet. The sandbox layer fills in {@code llmProxyUrl} during PREPARE.
     */
    static NetworkPolicy buildNetworkPolicy(
        CredentialMode mode,
        LlmProvider provider,
        @Nullable String jobToken,
        boolean allowInternet
    ) {
        if (mode == CredentialMode.PROXY) {
            String providerPath = provider.name().toLowerCase(Locale.ROOT);
            return new NetworkPolicy(allowInternet, null, jobToken, providerPath);
        }
        return new NetworkPolicy(true, null, null, null);
    }

    /** Read a classpath resource under {@link #AGENT_RESOURCE_PREFIX}. */
    public static byte[] loadClasspathResource(String relativePath) {
        String fullPath = AGENT_RESOURCE_PREFIX + relativePath;
        try (InputStream is = PiRuntimeFactory.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: " + fullPath);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + fullPath, e);
        }
    }

    /** Materialised Pi sandbox plan. Caller wraps in the appropriate per-agent spec record. */
    public record PiPlan(
        List<String> command,
        Map<String, String> environment,
        Map<String, byte[]> inputFiles,
        NetworkPolicy networkPolicy
    ) {
        public PiPlan {
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            inputFiles = Map.copyOf(Objects.requireNonNull(inputFiles, "inputFiles"));
            Objects.requireNonNull(networkPolicy, "networkPolicy");
        }
    }
}
