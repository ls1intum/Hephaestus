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
        String authSetup = LlmProxyAuthShell.build(spec.credentialMode(), spec.provider(), spec.credential(), env);

        // Settings live outside .pi/ so Pi's settings lock file lands on a writable mount.
        inputFiles.put(
            WorkspaceAbi.PI_RUNTIME_PREFIX + "settings.json",
            buildPracticeSettingsJson(spec.provider(), spec.modelName())
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

    /** Build settings JSON for a practice-review agent run. */
    byte[] buildPracticeSettingsJson(LlmProvider provider, @Nullable String modelName) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("defaultProvider", providerToken(provider));
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
