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
    private static final int MAX_AGENT_STEPS = 15;
    private static final int MAX_STDOUT_BUFFER_BYTES = 10 * 1024 * 1024;
    /** Buffer time (seconds) reserved for cleanup before container timeout. */
    private static final int TIMEOUT_BUFFER_SECONDS = 60;

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

        // Inject a restrictive agent definition that denies file/bash tool use.
        // OpenCode agents live at .opencode/agent/<name>.md in the workspace.
        inputFiles.put(".opencode/agent/practice-review.md", buildAgentDefinition());

        // Use a Node.js wrapper to invoke opencode with spawnSync.
        // This bypasses shell variable handling entirely — the prompt file is read
        // by Node and passed as a single argument array element, avoiding shell expansion
        // issues with large prompts containing special characters.
        int agentTimeoutMs = (request.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000;
        inputFiles.put(".run-opencode.mjs", buildRunnerScript(agentTimeoutMs));

        // In proxy mode, alias the generic LLM_PROXY_URL/LLM_PROXY_TOKEN env vars
        // to the provider-specific env vars that OpenCode's built-in providers expect.
        // This lets us use built-in providers (no npm install) even through the proxy.
        String proxyEnvSetup = buildProxyEnvAliases(request);

        // Initialize git repo from injected source files (same as ClaudeCodeAgentAdapter)
        String gitSetup =
            "cd /workspace/repo" +
            " && git init -q" +
            " && git config user.email agent@hephaestus" +
            " && git config user.name agent" +
            " && git add -A" +
            " && git commit -q --allow-empty -m 'pr-head'" +
            " && git branch -m pr" +
            " && git checkout -q -b target" +
            " && git apply -q --reverse /workspace/.context/diff.patch 2>/dev/null" +
            " && git add -A" +
            " && git commit -q --allow-empty -am 'target-base'" +
            " && git checkout -q pr" +
            " && cd /workspace && ";

        String command = gitSetup + "mkdir -p " + OUTPUT_PATH + proxyEnvSetup + " && node /workspace/.run-opencode.mjs";

        return new AgentSandboxSpec(
            IMAGE,
            List.of("sh", "-c", command),
            env,
            inputFiles,
            OUTPUT_PATH,
            null,
            AgentAdapter.buildNetworkPolicy(request)
        );
    }

    /**
     * Build an OpenCode agent definition with read-only tool access and safety settings.
     *
     * <p>Key settings:
     * <ul>
     *   <li>{@code temperature: 0} — deterministic output for reliable structured JSON</li>
     *   <li>{@code steps: MAX_AGENT_STEPS} — prevents runaway agentic loops within the sandbox timeout</li>
     *   <li>{@code doom_loop: deny} — prevents hang on infinite-loop detection prompts in non-interactive mode</li>
     *   <li>{@code external_directory: deny} — prevents hang on access-confirmation prompts</li>
     *   <li>Bash allowlist — read-only commands only (grep, find, cat, git log, git blame, etc.)</li>
     * </ul>
     */
    private byte[] buildAgentDefinition() {
        String agentMd = """
            ---
            description: Practice-aware code review agent. Analyzes diffs, explores codebases, and produces structured JSON findings.
            temperature: 0
            steps: %d
            permission:
              bash:
                "grep *": allow
                "find *": allow
                "cat *": allow
                "head *": allow
                "tail *": allow
                "wc *": allow
                "ls *": allow
                "tree *": allow
                "git log *": allow
                "git show *": allow
                "git diff *": allow
                "git blame *": allow
                "*": deny
              edit: deny
              read: allow
              glob: allow
              grep: allow
              list: allow
              write: deny
              webfetch: deny
              websearch: deny
              task: deny
              doom_loop: deny
              external_directory: deny
            ---

            You are an expert code review agent with full read access to the repository.
            The repository is cloned at /workspace/repo/. The diff, commits, and practice definitions
            are provided in the user prompt, but you should also explore the codebase to understand
            context, check related files, and verify your findings.

            Use tools to produce a thorough, high-quality review:
            - Read related source files to understand architectural context
            - Check if a pattern flagged in the diff is consistent with the rest of the codebase
            - Run grep/find to check for similar issues elsewhere
            - Verify import paths, dependency versions, or configuration files

            Your final output MUST be a valid JSON object. Output it as your final message — do NOT write it to a file.
            """.formatted(MAX_AGENT_STEPS);
        return agentMd.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AgentResult parseResult(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());

        byte[] resultFile = sandboxResult.outputFiles().get("result.json");
        if (resultFile != null && resultFile.length > 0) {
            String rawContent = new String(resultFile, StandardCharsets.UTF_8);
            String extracted = extractTextFromNdjson(rawContent);
            output.put("rawOutput", extracted != null ? extracted : rawContent);
        }

        // Capture debug.log for introspection
        byte[] debugLog = sandboxResult.outputFiles().get("debug.log");
        if (debugLog != null && debugLog.length > 0) {
            String debugContent = new String(debugLog, StandardCharsets.UTF_8);
            log.debug(
                "OpenCode debug log ({} bytes):\n{}",
                debugLog.length,
                debugContent.length() > 4096 ? debugContent.substring(debugContent.length() - 4096) : debugContent
            );
            output.put("debugLog", debugContent);
        }

        // Capture workspace listing for debugging file injection
        byte[] listing = sandboxResult.outputFiles().get("workspace-listing.txt");
        if (listing != null && listing.length > 0) {
            String listingContent = new String(listing, StandardCharsets.UTF_8);
            log.debug("OpenCode workspace listing:\n{}", listingContent);
            output.put("workspaceListing", listingContent);
        }

        return new AgentResult(success, output);
    }

    /**
     * Build a Node.js wrapper script that invokes opencode via spawnSync.
     * Reads the prompt from /workspace/.prompt, passes it as a single argument,
     * and writes stdout/stderr to output files.
     */
    private byte[] buildRunnerScript(int agentTimeoutMs) {
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
     * Extract the model's text response from OpenCode NDJSON streaming output.
     *
     * <p>OpenCode {@code --format json} writes NDJSON lines: {@code step_start}, {@code text},
     * {@code step_finish}. The actual model response is in the {@code text} event's
     * {@code part.text} field. We concatenate all text parts (usually just one) to produce
     * the final output.
     *
     * @param ndjsonContent raw NDJSON content from result.json
     * @return concatenated text content, or null if parsing fails or no text events found
     */
    String extractTextFromNdjson(String ndjsonContent) {
        if (ndjsonContent == null || ndjsonContent.isBlank()) {
            return null;
        }

        // Quick check: if it starts with '{' and contains "findings", it's already plain JSON
        String trimmed = ndjsonContent.trim();
        if (trimmed.startsWith("{") && !trimmed.contains("\n{")) {
            return ndjsonContent;
        }

        try {
            StringBuilder textContent = new StringBuilder();
            for (String line : ndjsonContent.split("\n")) {
                if (line.isBlank()) continue;
                var node = objectMapper.readTree(line);
                if ("text".equals(node.path("type").asText())) {
                    String text = node.path("part").path("text").asText(null);
                    if (text != null) {
                        textContent.append(text);
                    }
                }
            }
            return textContent.isEmpty() ? null : textContent.toString();
        } catch (Exception e) {
            log.warn("Failed to parse OpenCode NDJSON output: {}", e.getMessage());
            return null;
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
                    default -> throw new IllegalArgumentException(
                        "Unsupported LLM provider for OpenCode: " + request.llmProvider()
                    );
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
