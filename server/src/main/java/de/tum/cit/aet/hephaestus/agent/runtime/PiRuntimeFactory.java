package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared Pi-agent kernel. Builds the sandbox-level scaffolding (settings, provider spec, env,
 * workspace inputs, base command, classpath resources, network policy) that every Pi-based agent
 * reuses.
 *
 * <p>Stays domain-agnostic: callers supply a {@link PiPlanSpec} (resolved LLM behaviour, job token,
 * runner filename, precompute step). Nothing here knows about practices or chat sessions.
 *
 * <p><b>#1368 slice 5 — ONE credential path.</b> Every sandbox talks to the in-app LLM proxy over
 * {@code $LLM_PROXY_URL}/{@code $LLM_PROXY_TOKEN} (injected by the sandbox adapter from
 * {@link PiPlan#networkPolicy()}) and registers a single custom Pi provider named
 * {@code hephaestus} from {@link SandboxLayout#PROVIDER_CONFIG_FILENAME}, which this factory writes
 * from the resolved {@link PiPlanSpec}. The real provider API key never enters the container.
 */
@Component
public class PiRuntimeFactory {

    private static final Logger log = LoggerFactory.getLogger(PiRuntimeFactory.class);

    /** Grace window before the sandbox hard-kills the runner — must fire before that deadline. */
    public static final int TIMEOUT_BUFFER_SECONDS = 60;

    /**
     * Floor for the self-watchdog budget (ms). A spec sitting just above the {@link PiPlanSpec} minimum
     * (timeoutSeconds &gt; {@link #TIMEOUT_BUFFER_SECONDS}) yields a tiny computed budget once the buffer is
     * subtracted; this floor keeps the watchdog budget at a sane minimum so it is never effectively zero.
     * Kept strictly below {@code TIMEOUT_BUFFER_SECONDS * 1000} so the watchdog still fires before the SPI
     * hard kill.
     */
    static final long MIN_BUDGET_MS = (TIMEOUT_BUFFER_SECONDS - 1) * 1000L;

    static final String AGENT_RESOURCE_PREFIX = "agent/";

    private final ObjectMapper objectMapper;

    public PiRuntimeFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Build a Pi sandbox plan ready for the executor. */
    public PiPlan build(PiPlanSpec spec) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();

        inputFiles.put(SandboxLayout.PI_AGENT_PREFIX + "settings.json", buildPiSettingsJson(spec.upstreamModelId()));
        inputFiles.put(SandboxLayout.PROVIDER_CONFIG_FILENAME, buildProviderConfigJson(spec));

        // The scaffolding is the run's prompt template; its digest is the job's prompt version. settings.json
        // and pi-provider.json are deliberately EXCLUDED — they vary by model, which the job's config
        // snapshot already pins.
        Map<String, byte[]> promptScaffolding = new LinkedHashMap<>();
        promptScaffolding.put(SandboxLayout.ORCHESTRATOR_PATH, loadClasspathResource("pi-orchestrator.md"));
        promptScaffolding.put(
            SandboxLayout.RUNNER_SCRIPT_FILENAME,
            loadClasspathResource(spec.runnerProfile().runnerScript())
        );
        for (String sidecar : spec.runnerProfile().sidecarScripts()) {
            promptScaffolding.put(sidecar, loadClasspathResource(sidecar));
        }
        String promptDigest = ProvenanceDigest.rootDigestHex(promptScaffolding);
        inputFiles.putAll(promptScaffolding);
        inputFiles.putAll(spec.extraInputs());

        long agentTimeoutMs = Math.max(MIN_BUDGET_MS, (long) (spec.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        env.put("AGENT_BUDGET_MS", Long.toString(agentTimeoutMs));

        env.put("HOME", "/home/agent");
        env.put("XDG_CONFIG_HOME", "/home/agent/.config");
        env.put("TMPDIR", "/home/agent/.local/tmp");
        env.put("PI_CODING_AGENT_DIR", SandboxLayout.PI_AGENT_DIR);

        String workspaceRoot = SandboxLayout.WORKSPACE_ROOT;
        PiRunnerProfile profile = spec.runnerProfile();
        String nodeFlagsFragment = renderNodeFlags(profile.nodeFlags());
        String nodeEnvFragment = renderNodeEnv(profile.additionalEnv());

        String command =
            "mkdir -p " +
            SandboxLayout.OUTPUT_PATH +
            " /home/agent/.config /home/agent/.local/tmp && " +
            // Pi SDK ESM imports resolve from /<workspace>/node_modules. The agent-pi Dockerfile
            // exposes the SDK at /opt/pi-sdk/node_modules (a stable symlink to pnpm's
            // content-addressed global install). NODE_PATH would NOT work here — Node's ESM
            // resolver ignores NODE_PATH, only the CommonJS require() honors it.
            "ln -sf /opt/pi-sdk/node_modules " +
            workspaceRoot +
            "/node_modules && " +
            spec.precomputeStep() +
            nodeEnvFragment +
            "node " +
            nodeFlagsFragment +
            workspaceRoot +
            "/" +
            SandboxLayout.RUNNER_SCRIPT_FILENAME;

        NetworkPolicy networkPolicy = buildNetworkPolicy(spec.jobToken(), spec.allowInternet());

        log.debug(
            "Built Pi plan: timeout={}s, apiProtocol={}, model={}, files={}",
            spec.timeoutSeconds(),
            spec.apiProtocol(),
            spec.upstreamModelId(),
            inputFiles.size()
        );
        return new PiPlan(
            List.of("sh", "-c", command),
            Map.copyOf(env),
            Map.copyOf(inputFiles),
            networkPolicy,
            promptDigest
        );
    }

    private static String renderNodeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return "";
        }
        return String.join(" ", flags) + " ";
    }

    /** Renders env as {@code KEY=value} pairs. Values are NOT shell-quoted — add quoting here if a profile ever needs whitespace/metachars. */
    private static String renderNodeEnv(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            b.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        return b.toString();
    }

    /**
     * Build the settings JSON Pi loads at session start. {@code defaultProvider} always resolves to
     * the {@code hephaestus} provider that the runner script (pi-runner.mjs / pi-mentor-runner.mjs,
     * via the shared {@code pi-provider.mjs} helper) registers directly on the ModelRegistry before
     * {@code createAgentSession}, sidestepping the Pi 0.74.x race where findInitialModel runs ahead
     * of extension loading. {@code defaultModel} is the verbatim upstream model id — gateway-routed
     * deployments (for example, a gateway-qualified model id) and Pi's
     * exact-match lookup against {@code modelRegistry.find} see the same string.
     */
    public byte[] buildPiSettingsJson(String upstreamModelId) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("defaultProvider", "hephaestus");
        if (upstreamModelId != null && !upstreamModelId.isBlank()) {
            settings.put("defaultModel", upstreamModelId);
        }
        settings.put("transport", "sse");
        Map<String, Object> compaction = new LinkedHashMap<>();
        compaction.put("enabled", true);
        compaction.put("reserveTokens", 16384);
        settings.put("compaction", compaction);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(settings);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Pi settings", e);
        }
    }

    /**
     * Build {@code pi-provider.json} — the single, non-secret provider spec both runners read to
     * register the {@code hephaestus} Pi provider. {@code baseUrl} is intentionally NOT included here:
     * it is the sandbox-local {@code $LLM_PROXY_URL} env var, resolved by the sandbox adapter at
     * container-start time (after {@code {appServerIp}} template substitution) — baking it into this
     * classpath-shaped JSON would freeze a value the adapter has not resolved yet.
     */
    byte[] buildProviderConfigJson(PiPlanSpec spec) {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("apiProtocol", spec.apiProtocol());
        provider.put("modelId", spec.upstreamModelId());
        provider.put("supportsReasoning", spec.supportsReasoning());
        if (spec.contextWindow() != null) {
            provider.put("contextWindow", spec.contextWindow());
        }
        if (spec.maxOutputTokens() != null) {
            provider.put("maxOutputTokens", spec.maxOutputTokens());
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(provider);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize pi-provider.json", e);
        }
    }

    /**
     * Sandbox-layer fills in {@code llmProxyUrl} during PREPARE; this only emits the policy shape.
     * Proxy is now the ONLY mode (#1368 slice 5) — no per-config branching.
     */
    static NetworkPolicy buildNetworkPolicy(String jobToken, boolean allowInternet) {
        return new NetworkPolicy(allowInternet, null, jobToken);
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

    /**
     * Materialised Pi sandbox plan. Caller wraps in the appropriate per-agent spec record.
     *
     * @param promptDigest digest of the prompt scaffolding (orchestrator + runner + sidecars) — the run's
     *     prompt version
     */
    public record PiPlan(
        List<String> command,
        Map<String, String> environment,
        Map<String, byte[]> inputFiles,
        NetworkPolicy networkPolicy,
        String promptDigest
    ) {
        public PiPlan {
            command = List.copyOf(Objects.requireNonNull(command, "command"));
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            inputFiles = Map.copyOf(Objects.requireNonNull(inputFiles, "inputFiles"));
            Objects.requireNonNull(networkPolicy, "networkPolicy");
            Objects.requireNonNull(promptDigest, "promptDigest");
        }
    }
}
