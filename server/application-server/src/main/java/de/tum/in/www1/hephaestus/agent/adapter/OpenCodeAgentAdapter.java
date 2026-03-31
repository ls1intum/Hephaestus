package de.tum.in.www1.hephaestus.agent.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for the OpenCode CLI agent (provider-agnostic).
 *
 * <p>CLI: {@code opencode run "prompt" --format json}
 *
 * <p>OpenCode requires a JSON config file. In proxy mode, the config uses the built-in
 * provider (e.g. {@code openai/model}) and the shell command aliases the generic proxy
 * env vars ({@code LLM_PROXY_URL}, {@code LLM_PROXY_TOKEN}) to provider-specific ones
 * ({@code OPENAI_BASE_URL}, {@code OPENAI_API_KEY}). This avoids the {@code npm} custom
 * provider field which triggers a runtime package install that hangs when DNS is blocked.
 *
 * <p>The config is injected to {@code /workspace/opencode.json} where OpenCode auto-discovers it.
 */
public class OpenCodeAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeAgentAdapter.class);

    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-opencode:latest";
    static final String OUTPUT_PATH = "/workspace/.output";
    private static final int MAX_STDOUT_BUFFER_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DEBUG_LOG_DISPLAY_CHARS = 4096;
    /** Buffer time (seconds) reserved for cleanup before container timeout. */
    static final int TIMEOUT_BUFFER_SECONDS = 60;

    private final ObjectMapper objectMapper;

    OpenCodeAgentAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType agentType() {
        return AgentType.OPENCODE;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();

        configureAuth(request, env);
        byte[] configJson = buildConfigJson(request);
        inputFiles.put("opencode.json", configJson);
        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));

        // OpenCode agent definitions loaded from classpath resources.
        // The orchestrator (practice-review) spawns per-practice subagents via the task tool.
        inputFiles.put(
            ".opencode/agents/practice-review.md",
            loadClasspathResource("opencode-orchestrator.md")
        );
        inputFiles.put(
            ".opencode/agents/practice-analyzer.md",
            loadClasspathResource("opencode-practice-analyzer.md")
        );

        // Use a Node.js wrapper to invoke opencode with spawnSync.
        // This bypasses shell variable handling entirely — the prompt file is read
        // by Node and passed as a single argument array element, avoiding shell expansion
        // issues with large prompts containing special characters.
        long agentTimeoutMs = Math.max(0L, (long) (request.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        inputFiles.put(".run-opencode.mjs", buildRunnerScript(agentTimeoutMs));

        // In proxy mode, alias the generic LLM_PROXY_URL/LLM_PROXY_TOKEN env vars
        // to the provider-specific env vars that OpenCode's built-in providers expect.
        // This lets us use built-in providers (no npm install) even through the proxy.
        String proxyEnvSetup = buildProxyEnvAliases(request);

        // Redirect TMPDIR to the exec-allowed tmpfs. OpenCode's Node.js file watcher
        // extracts a native .node addon to $TMPDIR and dlopen()s it — the default /tmp
        // has noexec, causing "failed to map segment from shared object" errors.
        env.put("TMPDIR", "/home/agent/.local/tmp");

        String command =
            "mkdir -p " + OUTPUT_PATH + " /home/agent/.local/tmp" + proxyEnvSetup + " && node /workspace/.run-opencode.mjs";

        return new AgentSandboxSpec(
            IMAGE,
            List.of("sh", "-c", command),
            env,
            inputFiles,
            OUTPUT_PATH,
            null,
            AgentAdapter.buildNetworkPolicy(request),
            null
        );
    }

    @Override
    public AgentResult parseResult(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());

        AgentResult.LlmUsage usage = null;
        byte[] resultFile = sandboxResult.outputFiles().get("result.json");
        if (resultFile != null && resultFile.length > 0) {
            String rawContent = new String(resultFile, StandardCharsets.UTF_8);
            log.debug("NDJSON output: {} bytes", rawContent.length());
            NdjsonParseResult parsed = parseNdjson(rawContent);
            if (parsed != null) {
                output.put("rawOutput", parsed.text() != null ? parsed.text() : rawContent);
                usage = parsed.usage();
                if (usage != null) {
                    log.info(
                        "OpenCode usage: calls={}, inputTokens={}, outputTokens={}, cost={}",
                        usage.totalCalls(), usage.inputTokens(), usage.outputTokens(), usage.costUsd()
                    );
                }
            } else {
                output.put("rawOutput", rawContent);
            }
        }

        // Capture debug.log for introspection
        byte[] debugLog = sandboxResult.outputFiles().get("debug.log");
        if (debugLog != null && debugLog.length > 0) {
            String debugContent = new String(debugLog, StandardCharsets.UTF_8);
            log.debug(
                "OpenCode debug log ({} bytes):\n{}",
                debugLog.length,
                debugContent.length() > MAX_DEBUG_LOG_DISPLAY_CHARS
                    ? debugContent.substring(debugContent.length() - MAX_DEBUG_LOG_DISPLAY_CHARS)
                    : debugContent
            );
            output.put("debugLog", debugContent);
        }

        // Capture workspace listing for debugging file injection
        byte[] listing = sandboxResult.outputFiles().get("workspace-listing.txt");
        if (listing != null && listing.length > 0) {
            String listingContent = new String(listing, StandardCharsets.UTF_8);
            log.debug(
                "OpenCode workspace listing ({} bytes):\n{}",
                listing.length,
                listingContent.length() > MAX_DEBUG_LOG_DISPLAY_CHARS
                    ? listingContent.substring(0, MAX_DEBUG_LOG_DISPLAY_CHARS) + "..."
                    : listingContent
            );
            output.put("workspaceListing", listingContent);
        }

        return new AgentResult(success, output, usage);
    }

    /**
     * Build a Node.js wrapper script that invokes opencode via spawnSync.
     * Reads the prompt from /workspace/.prompt, passes it as a single argument,
     * and writes stdout/stderr to output files.
     */
    private byte[] buildRunnerScript(long agentTimeoutMs) {
        String script = """
            import { spawnSync } from 'child_process';
            import { readFileSync, writeFileSync, mkdirSync } from 'fs';

            const OUTPUT = '/workspace/.output';
            mkdirSync(OUTPUT, { recursive: true });

            const prompt = readFileSync('/workspace/.prompt', 'utf-8').trim();
            console.error(`Prompt loaded: ${prompt.length} chars`);

            const result = spawnSync('opencode', [
                'run',
                '--format', 'json',
                '--agent', 'practice-review',
                '--print-logs',
                '--log-level', 'WARN',
                '--', prompt
            ], {
                encoding: 'utf-8',
                maxBuffer: %d,
                timeout: %d,
                stdio: ['pipe', 'pipe', 'pipe']
            });

            writeFileSync(`${OUTPUT}/result.json`, result.stdout || '');
            writeFileSync(`${OUTPUT}/debug.log`,
                `STATUS=${result.status}\\nSIGNAL=${result.signal}\\nERROR=${result.error || ''}\\n---STDERR---\\n${result.stderr || ''}`
            );

            console.error(`OpenCode exit: status=${result.status}, signal=${result.signal}, stdout=${(result.stdout || '').length} bytes`);
            process.exit(result.status || 0);
            """.formatted(MAX_STDOUT_BUFFER_BYTES, agentTimeoutMs);
        return script.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build shell commands to alias LLM_PROXY_URL/LLM_PROXY_TOKEN to provider-specific
     * env vars. Returns empty string for non-proxy modes.
     */
    private String buildProxyEnvAliases(AgentAdapterRequest request) {
        if (request.credentialMode() != CredentialMode.PROXY) {
            return "";
        }
        return switch (request.llmProvider()) {
            case OPENAI -> " && export OPENAI_BASE_URL=$LLM_PROXY_URL && export OPENAI_API_KEY=$LLM_PROXY_TOKEN";
            case ANTHROPIC -> " && export ANTHROPIC_BASE_URL=$LLM_PROXY_URL && export ANTHROPIC_API_KEY=$LLM_PROXY_TOKEN";
        };
    }

    /**
     * Parsed OpenCode NDJSON output containing both text and usage data.
     */
    record NdjsonParseResult(String text, AgentResult.LlmUsage usage) {}

    /**
     * Parse OpenCode NDJSON streaming output, extracting both text and usage.
     *
     * <p>OpenCode {@code --format json} writes NDJSON lines: {@code step-start}, {@code text},
     * {@code step-finish}. The text response is in the {@code text} event's {@code part.text}
     * field. Usage/cost data is in the {@code step-finish} event's {@code tokens} and
     * {@code cost} fields.
     *
     * @param ndjsonContent raw NDJSON content from result.json
     * @return parsed text and usage, or null if parsing fails
     */
    NdjsonParseResult parseNdjson(String ndjsonContent) {
        if (ndjsonContent == null || ndjsonContent.isBlank()) {
            return null;
        }

        // Quick check: if it starts with '{' and contains "findings", it's already plain JSON
        String trimmed = ndjsonContent.trim();
        if (trimmed.startsWith("{") && !trimmed.contains("\n{")) {
            return new NdjsonParseResult(ndjsonContent, null);
        }

        try {
            StringBuilder textContent = new StringBuilder();
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            int totalReasoningTokens = 0;
            int totalCacheReadTokens = 0;
            int totalCacheWriteTokens = 0;
            double totalCost = 0.0;
            int stepCount = 0;
            String model = null;

            var seenTypes = new java.util.LinkedHashSet<String>();
            for (String line : ndjsonContent.split("\n")) {
                if (line.isBlank()) continue;
                var node = objectMapper.readTree(line);
                String type = node.path("type").asText("");
                seenTypes.add(type);

                if ("text".equals(type)) {
                    String text = node.path("part").path("text").asText(null);
                    if (text != null) {
                        textContent.append(text);
                    }
                } else if ("step_finish".equals(type) || "step-finish".equals(type)) {
                    stepCount++;
                    // Usage may be at top level or nested under "part"
                    var tokens = node.path("tokens");
                    if (tokens.isMissingNode()) {
                        tokens = node.path("part").path("tokens");
                    }
                    if (!tokens.isMissingNode()) {
                        totalInputTokens += tokens.path("input").asInt(0);
                        totalOutputTokens += tokens.path("output").asInt(0);
                        totalReasoningTokens += tokens.path("reasoning").asInt(0);
                        var cache = tokens.path("cache");
                        totalCacheReadTokens += cache.path("read").asInt(0);
                        totalCacheWriteTokens += cache.path("write").asInt(0);
                    }
                    double stepCost = node.path("cost").asDouble(0.0);
                    if (stepCost == 0.0) {
                        stepCost = node.path("part").path("cost").asDouble(0.0);
                    }
                    totalCost += stepCost;

                } else if ("step_start".equals(type) || "step-start".equals(type)) {
                    // Extract model name from the step metadata if available
                    String stepModel = node.path("model").asText(null);
                    if (stepModel == null) {
                        stepModel = node.path("part").path("model").asText(null);
                    }
                    if (stepModel != null) {
                        model = stepModel;
                    }
                }
            }
            log.debug("NDJSON event types: {}", seenTypes);

            AgentResult.LlmUsage usage = stepCount > 0
                ? new AgentResult.LlmUsage(
                    model,
                    totalInputTokens > 0 ? totalInputTokens : null,
                    totalOutputTokens > 0 ? totalOutputTokens : null,
                    totalReasoningTokens > 0 ? totalReasoningTokens : null,
                    totalCacheReadTokens > 0 ? totalCacheReadTokens : null,
                    totalCacheWriteTokens > 0 ? totalCacheWriteTokens : null,
                    totalCost > 0 ? totalCost : null,
                    stepCount
                )
                : null;

            String text = textContent.isEmpty() ? null : textContent.toString();
            return new NdjsonParseResult(text, usage);
        } catch (Exception e) {
            log.warn("Failed to parse OpenCode NDJSON output: {}", e.getMessage());
            return null;
        }
    }

    /** Classpath prefix for agent resource files. */
    private static final String AGENT_RESOURCE_PREFIX = "agent/";

    /** Load a classpath resource from the {@code agent/} directory. */
    private static byte[] loadClasspathResource(String relativePath) {
        String fullPath = AGENT_RESOURCE_PREFIX + relativePath;
        try (InputStream is = OpenCodeAgentAdapter.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: " + fullPath);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + fullPath, e);
        }
    }

    void configureAuth(AgentAdapterRequest request, Map<String, String> env) {
        switch (request.credentialMode()) {
            case PROXY -> {
                // Proxy env vars (LLM_PROXY_URL, LLM_PROXY_TOKEN) are set by DockerSandboxAdapter.
                // The shell command aliases them to provider-specific env vars (see buildProxyEnvAliases).
            }
            case API_KEY, OAUTH -> {
                // OpenCode does not distinguish OAUTH from API_KEY — both use the provider's
                // standard API key env var. OAuth access tokens work as bearer tokens in the
                // same header position.
                switch (request.llmProvider()) {
                    case ANTHROPIC -> env.put("ANTHROPIC_API_KEY", request.credential());
                    case OPENAI -> env.put("OPENAI_API_KEY", request.credential());
                }
            }
        }
    }

    /**
     * Build the OpenCode JSON config as serialized bytes.
     *
     * <p>Uses Jackson ObjectMapper for safe JSON generation — all values are properly escaped,
     * preventing JSON injection through model names or other user-supplied strings.
     */
    byte[] buildConfigJson(AgentAdapterRequest request) {
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new IllegalArgumentException(
                "modelName is required for OpenCode — unlike Claude Code, OpenCode has no default model"
            );
        }
        String model = request.modelName();
        Map<String, Object> config = new LinkedHashMap<>();

        // Always use built-in provider prefix — avoids custom npm provider that requires
        // runtime package install (blocks on DNS-restricted sandboxes).
        // In proxy mode, OPENAI_BASE_URL/ANTHROPIC_BASE_URL env vars redirect to the proxy.
        String providerPrefix = switch (request.llmProvider()) {
            case ANTHROPIC -> "anthropic";
            case OPENAI -> "openai";
        };
        config.put("model", providerPrefix + "/" + model);

        config.put("share", "disabled");
        config.put("autoupdate", false);

        // Compaction prevents context overflow when the agent does many tool calls
        Map<String, Object> compaction = new LinkedHashMap<>();
        compaction.put("auto", true);
        compaction.put("prune", true);
        config.put("compaction", compaction);

        return serializeJson(config);
    }

    private byte[] serializeJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenCode config", e);
        }
    }
}
