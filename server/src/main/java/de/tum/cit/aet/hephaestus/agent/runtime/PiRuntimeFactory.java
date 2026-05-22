package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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

    public static final String OUTPUT_PATH = WorkspaceAbi.OUTPUT_PATH;

    /** Grace window before the sandbox hard-kills the runner — must fire before that deadline. */
    public static final int TIMEOUT_BUFFER_SECONDS = 60;

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
        inputFiles.put(WorkspaceAbi.RUNNER_SCRIPT_FILENAME, loadClasspathResource(spec.runnerProfile().runnerScript()));
        inputFiles.putAll(spec.extraInputs());

        long agentTimeoutMs = Math.max(60_000L, (long) (spec.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        env.put("AGENT_BUDGET_MS", Long.toString(agentTimeoutMs));

        env.put("HOME", "/home/agent");
        env.put("XDG_CONFIG_HOME", "/home/agent/.config");
        env.put("TMPDIR", "/home/agent/.local/tmp");
        env.put("PI_CODING_AGENT_DIR", WorkspaceAbi.PI_AGENT_DIR);

        // Azure's `azure-openai-responses` provider hard-defaults the model id to "gpt-5.2"; route
        // both that and the configured model to the same Azure deployment.
        if (spec.provider() == LlmProvider.AZURE_OPENAI) {
            String deployment = (spec.modelName() != null && !spec.modelName().isBlank())
                ? spec.modelName()
                : "gpt-5.4-mini";
            env.put("AZURE_OPENAI_DEPLOYMENT_NAME_MAP", deployment + "=" + deployment + ",gpt-5.2=" + deployment);
        }

        String workspaceRoot = WorkspaceAbi.WORKSPACE_ROOT;
        PiRunnerProfile profile = spec.runnerProfile();
        String nodeFlagsFragment = renderNodeFlags(profile.nodeFlags());
        String nodeEnvFragment = renderNodeEnv(profile.additionalEnv());

        String command =
            authSetup +
            "mkdir -p " +
            WorkspaceAbi.OUTPUT_PATH +
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

    /** Map {@link LlmProvider} to its Pi provider token. */
    static String providerToken(LlmProvider provider) {
        return switch (provider) {
            case AZURE_OPENAI -> "azure-openai-responses";
            case OPENAI -> "openai";
            case ANTHROPIC -> "anthropic";
        };
    }

    /** Two-arg overload for tests that don't exercise custom-provider routing. */
    byte[] buildPiSettingsJson(LlmProvider provider, @Nullable String modelName) {
        return buildPiSettingsJson(provider, modelName, false);
    }

    /**
     * Build the settings JSON Pi loads at session start. When {@code useCustomProvider} is true,
     * {@code defaultProvider} routes through the {@code hephaestus} extension (see
     * {@link #buildExtensionFile}). {@code defaultModel} is the verbatim model id the extension
     * registers — both gateway-routed gateways (e.g. TUM GPU expects {@code openai/gpt-oss-120b}
     * as the wire id) and Pi's exact-match lookup against {@code modelRegistry.find} see the
     * same string. With {@code defaultProvider="hephaestus"} pinned explicitly, Pi does not
     * reinterpret slashes as a provider prefix.
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
        } catch (JacksonException e) {
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
     * Emit the Pi extension that registers the {@code hephaestus} custom provider. The provider
     * reads its base URL, API key, and model id from env vars set by {@link LlmProxyAuthShell};
     * Pi auto-discovers extensions in the agent dir via jiti at session start.
     */
    public byte[] buildExtensionFile(PiPlanSpec spec) {
        // TS source typechecked at CI time via the agent-extensions npm workspace; bump in
        // lockstep with MentorLiveLlmTest.PI_SDK_VERSION.
        String resource =
            spec.provider() == LlmProvider.ANTHROPIC
                ? "extensions/provider-anthropic.ts"
                : "extensions/provider-openai.ts";
        return loadClasspathResource(resource);
    }

    /** Sandbox-layer fills in {@code llmProxyUrl} during PREPARE; this only emits the policy shape. */
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
