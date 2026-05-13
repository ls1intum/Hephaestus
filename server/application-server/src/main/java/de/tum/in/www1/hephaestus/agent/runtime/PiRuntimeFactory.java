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
                WorkspaceAbi.PI_RUNTIME_PREFIX + "extensions/hephaestus-provider.ts",
                buildExtensionFile(spec)
            );
        }

        // Settings live outside .pi/ so Pi's settings lock file lands on a writable mount.
        inputFiles.put(
            WorkspaceAbi.PI_RUNTIME_PREFIX + "settings.json",
            buildPiSettingsJson(spec.provider(), spec.modelName(), useCustomProvider)
        );
        inputFiles.put(WorkspaceAbi.ORCHESTRATOR_PATH, loadClasspathResource("pi-orchestrator.md"));
        inputFiles.put(WorkspaceAbi.RUNNER_SCRIPT_FILENAME, loadClasspathResource(spec.runnerScript()));
        inputFiles.putAll(spec.extraInputs());

        long agentTimeoutMs = Math.max(60_000L, (long) (spec.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        env.put("AGENT_BUDGET_MS", Long.toString(agentTimeoutMs));

        // Redirect writable runtime state away from the read-only /workspace mount.
        env.put("HOME", "/home/agent");
        env.put("XDG_CONFIG_HOME", "/home/agent/.config");
        env.put("TMPDIR", "/home/agent/.local/tmp");
        env.put("PI_CODING_AGENT_DIR", "/home/agent/.pi");

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
        String extensionCopyStep = useCustomProvider
            ? "mkdir -p /home/agent/.pi/extensions && cp " +
              workspaceRoot +
              "/" +
              WorkspaceAbi.PI_RUNTIME_PREFIX +
              "extensions/hephaestus-provider.ts /home/agent/.pi/extensions/hephaestus-provider.ts && "
            : "";
        String command =
            authSetup +
            "mkdir -p " +
            WorkspaceAbi.OUTPUT_PATH +
            " /home/agent/.pi /home/agent/.config /home/agent/.local/tmp && " +
            // Pi SDK ESM imports require a workspace-local node_modules.
            "ln -sf /usr/local/lib/node_modules " +
            workspaceRoot +
            "/node_modules && " +
            "cp " +
            workspaceRoot +
            "/" +
            WorkspaceAbi.PI_RUNTIME_PREFIX +
            "settings.json /home/agent/.pi/settings.json && " +
            "cp " +
            workspaceRoot +
            "/" +
            WorkspaceAbi.ORCHESTRATOR_PATH +
            " /home/agent/.pi/" +
            WorkspaceAbi.ORCHESTRATOR_FILENAME +
            " && " +
            extensionCopyStep +
            spec.precomputeStep() +
            "node " +
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
        String api = spec.provider() == LlmProvider.ANTHROPIC ? "anthropic-messages" : "openai-completions";
        // Model fields are environment-driven (jiti executes the TS at runtime in Node), so the
        // model id is whatever PI_HEPHAESTUS_MODEL holds. Defaults are safe for chat-completions
        // OpenAI-compat providers — cost is zero (server-side pricing layer owns the canonical
        // numbers), context window is generous, max tokens is the conventional 4096.
        String ts =
            "import type { ExtensionAPI } from \"@earendil-works/pi-coding-agent\";\n" +
            "\n" +
            "export default function (pi: ExtensionAPI) {\n" +
            "  const baseUrl = process.env.PI_HEPHAESTUS_BASE_URL;\n" +
            "  const modelId = process.env.PI_HEPHAESTUS_MODEL ?? \"" +
            (spec.modelName() != null ? spec.modelName() : "") +
            "\";\n" +
            "  if (!baseUrl || !modelId) {\n" +
            "    throw new Error(\"hephaestus provider needs PI_HEPHAESTUS_BASE_URL + PI_HEPHAESTUS_MODEL\");\n" +
            "  }\n" +
            "  pi.registerProvider(\"hephaestus\", {\n" +
            "    name: \"Hephaestus Gateway\",\n" +
            "    baseUrl,\n" +
            "    apiKey: \"PI_HEPHAESTUS_API_KEY\",\n" +
            "    authHeader: true,\n" +
            "    api: \"" +
            api +
            "\",\n" +
            "    models: [\n" +
            "      {\n" +
            "        id: modelId,\n" +
            "        name: modelId,\n" +
            "        reasoning: false,\n" +
            "        input: [\"text\"],\n" +
            "        cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },\n" +
            "        contextWindow: 131072,\n" +
            "        maxTokens: 4096,\n" +
            "      },\n" +
            "    ],\n" +
            "  });\n" +
            "}\n";
        return ts.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
