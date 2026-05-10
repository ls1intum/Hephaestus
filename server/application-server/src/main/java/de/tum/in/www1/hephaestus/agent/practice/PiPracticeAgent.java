package de.tum.in.www1.hephaestus.agent.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Pi practice-detection agent. Spawns agent-pi with a Node.js runner that drives the Pi SDK,
 * then parses its result.json (with review-state.json fallback) into an {@link AgentResult}.
 */
@Service
public class PiPracticeAgent {

    private static final Logger log = LoggerFactory.getLogger(PiPracticeAgent.class);

    static final String DEFAULT_IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";
    static final String OUTPUT_PATH = "/workspace/.output";
    private static final String AGENT_RESOURCE_PREFIX = "agent/";
    private static final int MAX_BRACE_ATTEMPTS = 5;
    /** Reserved for retries + container cleanup before the sandbox kills the runner. */
    static final int TIMEOUT_BUFFER_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final String image;

    public PiPracticeAgent(
        ObjectMapper objectMapper,
        @Value("${hephaestus.agent.pi.image:" + DEFAULT_IMAGE + "}") String image
    ) {
        this.objectMapper = objectMapper;
        this.image = image;
    }

    public PracticeSandboxSpec buildSandboxSpec(PracticeAgentRequest request) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        String authSetup = buildAuthSetup(request, env);

        // Settings live outside /workspace/.pi so Pi's settings lock file lands on a writable mount.
        inputFiles.put(".pi-runtime/settings.json", buildSettingsJson(request));
        inputFiles.put(".pi/AGENTS.md", loadClasspathResource("pi-orchestrator.md"));
        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));

        long agentTimeoutMs = Math.max(60_000L, (long) (request.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        inputFiles.put(".run-pi.mjs", loadClasspathResource("pi-runner.mjs"));
        env.put("AGENT_BUDGET_MS", Long.toString(agentTimeoutMs));

        // Redirect writable runtime state away from the read-only /workspace mount.
        env.put("HOME", "/home/agent");
        env.put("XDG_CONFIG_HOME", "/home/agent/.config");
        env.put("TMPDIR", "/home/agent/.local/tmp");
        env.put("PI_CODING_AGENT_DIR", "/home/agent/.pi");

        // Pi's azure-openai-responses provider hard-defaults the model to "gpt-5.2" — the deployment
        // map routes both that and the configured model to the correct Azure deployment.
        if (request.llmProvider() == LlmProvider.AZURE_OPENAI) {
            String deployment = (request.modelName() != null && !request.modelName().isBlank())
                ? request.modelName()
                : "gpt-5.4-mini";
            env.put("AZURE_OPENAI_DEPLOYMENT_NAME_MAP", deployment + "=" + deployment + ",gpt-5.2=" + deployment);
        }

        String command =
            authSetup +
            "mkdir -p " +
            OUTPUT_PATH +
            " /home/agent/.pi /home/agent/.config /home/agent/.local/tmp && " +
            // Pi SDK ESM imports require a workspace-local node_modules.
            "ln -sf /usr/local/lib/node_modules /workspace/node_modules && " +
            "cp /workspace/.pi-runtime/settings.json /home/agent/.pi/settings.json && " +
            "cp /workspace/.pi/AGENTS.md /home/agent/.pi/AGENTS.md && " +
            buildPrecomputeStep() +
            "node /workspace/.run-pi.mjs";

        return new PracticeSandboxSpec(
            image,
            List.of("sh", "-c", command),
            env,
            inputFiles,
            OUTPUT_PATH,
            null,
            buildNetworkPolicy(request),
            null
        );
    }

    /** Build Pi settings JSON via Jackson (escapes user-supplied model names). */
    byte[] buildSettingsJson(PracticeAgentRequest request) {
        Map<String, Object> settings = new LinkedHashMap<>();

        String provider = switch (request.llmProvider()) {
            case AZURE_OPENAI -> "azure-openai-responses";
            case OPENAI -> "openai";
            case ANTHROPIC -> "anthropic";
        };
        settings.put("defaultProvider", provider);

        if (request.modelName() != null && !request.modelName().isBlank()) {
            settings.put("defaultModel", request.modelName());
        }

        settings.put("transport", "sse");

        Map<String, Object> compaction = new LinkedHashMap<>();
        compaction.put("enabled", true);
        compaction.put("reserveTokens", 16384);
        settings.put("compaction", compaction);

        return serializeJson(settings);
    }

    /**
     * Parse Pi output, falling back to {@code review-state.json} when {@code result.json} is absent.
     * Sanitises Swift {@code \(…)} string-interpolation escapes that produce invalid JSON.
     */
    public AgentResult parseResult(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());
        // Surface watchdog kills (runner writes this file before exit 3).
        addWatchdogState(output, sandboxResult.outputFiles().get("watchdog-killed.json"));
        AgentResult.LlmUsage usage = parseUsage(sandboxResult.outputFiles().get("usage.json"));
        addRunnerDebug(output, sandboxResult.outputFiles().get("runner-debug.json"));

        byte[] resultFile = sandboxResult.outputFiles().get("result.json");
        if (resultFile == null) {
            resultFile = buildResultFromReviewState(sandboxResult.outputFiles().get("review-state.json"));
        }
        if (resultFile == null) {
            return new AgentResult(success, output, usage);
        }

        String rawContent = sanitizeSwiftEscapes(new String(resultFile, StandardCharsets.UTF_8));
        if (isValidJsonWithFindings(rawContent)) {
            output.put("rawOutput", rawContent);
            return new AgentResult(success, output, usage);
        }

        String extracted = extractJsonFromText(rawContent);
        output.put("rawOutput", extracted != null ? extracted : rawContent);
        return new AgentResult(success, output, usage);
    }

    private AgentResult.LlmUsage parseUsage(byte[] usageFile) {
        if (usageFile == null || usageFile.length == 0) {
            return null;
        }

        try {
            JsonNode usageNode = objectMapper.readTree(usageFile);
            int totalCalls = usageNode.path("totalCalls").asInt(0);
            if (totalCalls <= 0) {
                return null;
            }

            String model = usageNode.path("model").isTextual() ? usageNode.path("model").asText() : null;
            Integer inputTokens = usageNode.has("inputTokens") ? usageNode.path("inputTokens").asInt(0) : null;
            Integer outputTokens = usageNode.has("outputTokens") ? usageNode.path("outputTokens").asInt(0) : null;
            Integer cacheReadTokens = usageNode.has("cacheReadTokens")
                ? usageNode.path("cacheReadTokens").asInt(0)
                : null;
            Integer cacheWriteTokens = usageNode.has("cacheWriteTokens")
                ? usageNode.path("cacheWriteTokens").asInt(0)
                : null;
            Double costUsd = usageNode.has("costUsd") ? usageNode.path("costUsd").asDouble(0.0) : null;

            return new AgentResult.LlmUsage(
                model,
                inputTokens,
                outputTokens,
                null,
                cacheReadTokens,
                cacheWriteTokens,
                costUsd,
                totalCalls
            );
        } catch (Exception e) {
            log.warn("Failed to parse Pi usage output", e);
            return null;
        }
    }

    private void addRunnerDebug(Map<String, Object> output, byte[] runnerDebugFile) {
        if (runnerDebugFile == null || runnerDebugFile.length == 0) {
            return;
        }

        try {
            output.put("runnerDebug", objectMapper.readValue(runnerDebugFile, Object.class));
        } catch (Exception e) {
            log.warn("Failed to parse Pi runner debug output", e);
        }
    }

    private void addWatchdogState(Map<String, Object> output, byte[] watchdogFile) {
        if (watchdogFile == null || watchdogFile.length == 0) {
            return;
        }

        try {
            output.put("watchdogKilled", objectMapper.readValue(watchdogFile, Object.class));
        } catch (Exception e) {
            log.warn("Failed to parse Pi watchdog marker", e);
        }
    }

    private byte[] buildResultFromReviewState(byte[] reviewStateFile) {
        if (reviewStateFile == null || reviewStateFile.length == 0) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(reviewStateFile);
            JsonNode findings = root.get("findings");
            if (findings == null || !findings.isArray() || findings.isEmpty()) {
                return null;
            }

            Map<String, Object> assembled = new LinkedHashMap<>();
            assembled.put("findings", objectMapper.treeToValue(findings, Object.class));

            JsonNode delivery = root.get("delivery");
            if (delivery != null && delivery.isObject()) {
                JsonNode mrNote = delivery.get("mrNote");
                if (mrNote != null && mrNote.isTextual() && !mrNote.asText().isBlank()) {
                    assembled.put("delivery", Map.of("mrNote", mrNote.asText()));
                }
            }

            return objectMapper.writeValueAsBytes(assembled);
        } catch (Exception e) {
            log.warn("Failed to rebuild Pi result from durable review state", e);
            return null;
        }
    }

    /**
     * Fix invalid JSON escapes produced when the LLM quotes Swift string interpolation.
     * Swift's backslash-paren becomes an invalid escape in JSON.
     */
    private String sanitizeSwiftEscapes(String json) {
        if (json == null || !json.contains("\\")) {
            return json;
        }
        StringBuilder sb = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (
                    next == '"' ||
                    next == '\\' ||
                    next == '/' ||
                    next == 'b' ||
                    next == 'f' ||
                    next == 'n' ||
                    next == 'r' ||
                    next == 't' ||
                    next == 'u'
                ) {
                    sb.append(c); // valid escape, keep backslash
                } else {
                    // invalid escape like \( — drop the backslash
                    continue;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Find the first '{'…'}' object containing 'findings' (max {@value MAX_BRACE_ATTEMPTS} attempts). */
    private String extractJsonFromText(String text) {
        int searchFrom = 0;
        char[] chars = text.toCharArray();
        int attempts = 0;
        while (searchFrom < chars.length && attempts < MAX_BRACE_ATTEMPTS) {
            int bracePos = text.indexOf('{', searchFrom);
            if (bracePos == -1) {
                break;
            }
            attempts++;
            try (var parser = objectMapper.getFactory().createParser(chars, bracePos, chars.length - bracePos)) {
                JsonNode node = objectMapper.readTree(parser);
                if (node != null && node.isObject() && node.has("findings")) {
                    return objectMapper.writeValueAsString(node);
                }
            } catch (Exception e) {
                log.trace("No JSON object at position {}: {}", bracePos, e.getMessage());
            }
            searchFrom = bracePos + 1;
        }
        return null;
    }

    private boolean isValidJsonWithFindings(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            return node != null && node.isObject() && node.has("findings");
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] serializeJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Pi settings", e);
        }
    }

    /**
     * Build the shell auth prefix; in API_KEY mode credentials go through {@code export} (not the
     * env map) to bypass the sandbox blocked-prefix filter for {@code AZURE_*} variables.
     */
    String buildAuthSetup(PracticeAgentRequest request, Map<String, String> env) {
        return switch (request.credentialMode()) {
            case PROXY -> buildProxyAuthSetup(request.llmProvider());
            case API_KEY -> buildApiKeyAuthSetup(request.llmProvider(), request.credential(), env);
            case OAUTH -> {
                // Pi does not have a dedicated OAuth mode; treat as API key
                yield buildApiKeyAuthSetup(request.llmProvider(), request.credential(), env);
            }
        };
    }

    private String buildProxyAuthSetup(LlmProvider provider) {
        return switch (provider) {
            case AZURE_OPENAI ->
                // Pi appends /responses to AZURE_OPENAI_BASE_URL — must end at /openai (not /openai/v1)
                // for the 2025-04-01-preview api-version.
                "export AZURE_OPENAI_BASE_URL=\"$LLM_PROXY_URL/openai\"" +
                " AZURE_OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"" +
                " AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"" +
                " && ";
            case OPENAI -> "export OPENAI_BASE_URL=\"$LLM_PROXY_URL\"" +
            " OPENAI_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " && ";
            case ANTHROPIC -> "export ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"" +
            " ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " && ";
        };
    }

    private String buildApiKeyAuthSetup(LlmProvider provider, String credential, Map<String, String> env) {
        return switch (provider) {
            case AZURE_OPENAI -> "export AZURE_OPENAI_API_KEY=" +
            shellQuote(credential) +
            " AZURE_OPENAI_API_VERSION=\"2025-04-01-preview\"" +
            " && ";
            case OPENAI -> {
                env.put("OPENAI_API_KEY", credential);
                yield "";
            }
            case ANTHROPIC -> {
                env.put("ANTHROPIC_API_KEY", credential);
                yield "";
            }
        };
    }

    /** Single-quote a value for safe shell interpolation (escapes embedded single quotes). */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * PROXY mode forwards {@code allowInternet} + {@code jobToken}; direct modes always allow internet.
     * The sandbox layer fills in {@code llmProxyUrl} during PREPARE.
     */
    static NetworkPolicy buildNetworkPolicy(PracticeAgentRequest request) {
        if (request.credentialMode() == CredentialMode.PROXY) {
            String providerPath = request.llmProvider().name().toLowerCase(Locale.ROOT);
            return new NetworkPolicy(request.allowInternet(), null, request.jobToken(), providerPath);
        }
        return new NetworkPolicy(true, null, null, null);
    }

    /** Run precompute scripts via Bun before the agent. Failure is non-fatal. */
    static String buildPrecomputeStep() {
        return (
            "(mkdir -p /workspace/.precompute-out/practices" +
            " && cp /workspace/.precompute/practices/*.ts /workspace/.precompute-out/practices/" +
            " && ln -sf /opt/precompute/lib /workspace/.precompute-out/lib" +
            " && bun run /opt/precompute/runner.ts" +
            " --repo /workspace/repo" +
            " --diff /workspace/.context/diff.patch" +
            " --metadata /workspace/.context/metadata.json" +
            " --output /workspace/.precompute-out" +
            " > /tmp/precompute-runner.log 2>&1" +
            " || { echo '[precompute] failed, continuing without hints'" +
            " && cp /tmp/precompute-runner.log /workspace/.precompute-out/precompute-runner.log 2>/dev/null" +
            " ; tail -200 /tmp/precompute-runner.log 2>/dev/null" +
            " ; true; }) && "
        );
    }

    static byte[] loadClasspathResource(String relativePath) {
        String fullPath = AGENT_RESOURCE_PREFIX + relativePath;
        try (InputStream is = PiPracticeAgent.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: " + fullPath);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + fullPath, e);
        }
    }
}
