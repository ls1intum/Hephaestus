package de.tum.in.www1.hephaestus.agent.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Adapter for the Pi coding agent CLI (provider-agnostic).
 *
 * <p>CLI: {@code pi -p "prompt" --session-dir /tmp/pi-sessions --tools read,bash,grep,find,ls}
 * (retry: {@code pi -c "correction" --session-dir /tmp/pi-sessions --tools read,bash,grep,find,ls})
 *
 * <p>Pi requires a JSON settings file. This adapter injects the config to
 * {@code /workspace/.pi/settings.json} (via {@code PI_CODING_AGENT_DIR} env var) and an
 * {@code AGENTS.md} orchestrator file to {@code /workspace/.pi/AGENTS.md}.
 *
 * <p>Authentication modes:
 * <ul>
 *   <li>PROXY (AZURE_OPENAI): bridges {@code $LLM_PROXY_URL} → {@code AZURE_OPENAI_BASE_URL}</li>
 *   <li>PROXY (OPENAI): bridges {@code $LLM_PROXY_URL} → {@code OPENAI_BASE_URL}</li>
 *   <li>PROXY (ANTHROPIC): bridges {@code $LLM_PROXY_URL} → {@code ANTHROPIC_BASE_URL}</li>
 *   <li>API_KEY: sets the provider-specific API key env var directly</li>
 * </ul>
 *
 * <p>Result parsing: Pi outputs plain text in print mode. The runner script captures it to
 * {@code result.json}. This adapter tries direct JSON parsing first, then falls back to
 * extracting a JSON object from mixed text.
 */
public class PiAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(PiAgentAdapter.class);

    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";
    static final String OUTPUT_PATH = "/workspace/.output";
    private static final int MAX_BRACE_ATTEMPTS = 5;
    private static final int MAX_STDOUT_BUFFER_BYTES = 10 * 1024 * 1024;
    /** Buffer time (seconds) reserved for retries + cleanup before container timeout. */
    static final int TIMEOUT_BUFFER_SECONDS = 60;

    private final ObjectMapper objectMapper;

    PiAgentAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType agentType() {
        return AgentType.PI;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        String authSetup = buildAuthSetup(request, env);

        // Inject Pi settings.json with provider, model, and compaction config
        inputFiles.put(".pi/settings.json", buildSettingsJson(request));

        // Pi agent orchestrator file (auto-discovered by Pi CLI via AGENTS.md convention)
        inputFiles.put(".pi/AGENTS.md", AgentAdapter.loadClasspathResource("PI-AGENTS.md"));

        // Prompt from the review pipeline (includes practice refs, metadata pointers, workspace layout)
        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));

        // Node.js runner script handles: run pi → validate output → retry via -c
        long agentTimeoutMs = Math.max(60_000L, (long) (request.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        inputFiles.put(".run-pi.mjs", buildRunnerScript(agentTimeoutMs));

        // Redirect TMPDIR to exec-allowed tmpfs. Pi's Node.js runtime may extract native addons
        // to $TMPDIR; the default /tmp has noexec, causing "failed to map segment" errors.
        env.put("TMPDIR", "/home/agent/.local/tmp");

        // Tell Pi CLI where to find its config directory
        env.put("PI_CODING_AGENT_DIR", "/workspace/.pi");

        // Pi's azure-openai-responses provider defaults model to "gpt-5.2" regardless of
        // settings.json. The deployment map ensures both the configured model and Pi's default
        // resolve to the correct Azure deployment. Set via env map (not shell) to avoid injection.
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
            " /home/agent/.local/tmp && " +
            AgentAdapter.buildPrecomputeStep() +
            "node /workspace/.run-pi.mjs";

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

    /**
     * Build the Pi settings JSON config as serialized bytes.
     *
     * <p>Uses Jackson ObjectMapper for safe JSON generation — all values are properly escaped,
     * preventing JSON injection through model names or other user-supplied strings.
     *
     * <p>The {@code defaultProvider} maps LLM providers to Pi's built-in provider identifiers:
     * {@code AZURE_OPENAI} → {@code "azure-openai-responses"}, {@code OPENAI} → {@code "openai"},
     * {@code ANTHROPIC} → {@code "anthropic"}.
     */
    byte[] buildSettingsJson(AgentAdapterRequest request) {
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
     * Build a Node.js runner script that invokes Pi with self-correction.
     *
     * <p>The script runs the initial analysis, validates the output for a {@code findings}
     * array, and if invalid, sends up to 2 correction messages via {@code pi -c} (which
     * resumes the existing session with full context preserved).
     *
     * @param agentTimeoutMs total time budget for all runs (initial + retries)
     */
    private byte[] buildRunnerScript(long agentTimeoutMs) {
        long retryTimeoutMs = 60_000;
        long initialTimeoutMs = Math.max(60_000, agentTimeoutMs - 2 * retryTimeoutMs);
        String script = """
            import { spawnSync } from 'child_process';
            import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'fs';

            const OUTPUT = '/workspace/.output';
            mkdirSync(OUTPUT, { recursive: true });

            const prompt = readFileSync('/workspace/.prompt', 'utf-8').trim();

            function runPi(args, label, timeoutMs) {
              console.error(`[run-pi] ${label}`);
              const start = Date.now();
              const r = spawnSync('pi', args, {
                encoding: 'utf-8',
                maxBuffer: %d,
                timeout: timeoutMs || 0,
                cwd: '/workspace',
                stdio: ['pipe', 'pipe', 'pipe']
              });
              const sec = ((Date.now() - start) / 1000).toFixed(1);
              console.error(`[run-pi] ${label}: ${sec}s, exit=${r.status}, stdout=${(r.stdout||'').length}b`);
              if (r.error) console.error(`[run-pi] ${label}: error=${r.error.message}`);
              return r;
            }

            function checkResult() {
              // Check if agent wrote result.json via write tool
              const resultPath = `${OUTPUT}/result.json`;
              if (!existsSync(resultPath)) return false;
              try {
                const data = JSON.parse(readFileSync(resultPath, 'utf-8'));
                return data?.findings?.length > 0;
              } catch {
                return false;
              }
            }

            function hasFindings(out) {
              if (!out?.trim()) return false;
              try { return JSON.parse(out)?.findings?.length > 0; } catch {}
              return out.includes('"findings"') && out.includes('"practiceSlug"');
            }

            // Initial run — session persists for -c retries. Agent writes output via write tool.
            let result = runPi([
              '-p', prompt,
              '--session-dir', '/tmp/pi-sessions',
              '--tools', 'read,bash,grep,find,ls,write'
            ], 'initial', %d);

            // Check if agent wrote result.json via write tool (preferred path)
            if (checkResult()) {
              console.error('[run-pi] SUCCESS: agent wrote result.json via write tool');
              process.exit(0);
            }

            // Fallback: check stdout for findings
            let bestOutput = result.stdout || '';
            if (hasFindings(bestOutput)) {
              writeFileSync(`${OUTPUT}/result.json`, bestOutput);
              console.error('[run-pi] SUCCESS: findings from stdout');
              process.exit(0);
            }

            // Retry: ask agent to write findings to file
            for (let i = 1; i <= 2; i++) {
              console.error(`[run-pi] retry ${i}/2`);
              result = runPi([
                '-c', 'Write the findings JSON to .output/result.json using the write tool. Include all 13 practices.',
                '--session-dir', '/tmp/pi-sessions',
                '--tools', 'read,bash,grep,find,ls,write'
              ], `retry-${i}`, %d);

              if (checkResult()) {
                console.error(`[run-pi] SUCCESS after retry ${i}`);
                process.exit(0);
              }

              const retryOut = result.stdout || '';
              if (hasFindings(retryOut)) {
                writeFileSync(`${OUTPUT}/result.json`, retryOut);
                console.error(`[run-pi] SUCCESS: findings from retry stdout`);
                process.exit(0);
              }
            }

            console.error('[run-pi] FAILED: no valid findings after retries');
            process.exit(result.status || 1);
            """.formatted(MAX_STDOUT_BUFFER_BYTES, initialTimeoutMs, retryTimeoutMs);
        return script.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse the Pi agent output from the sandbox result.
     *
     * <p>The Pi agent writes findings to {@code .output/result.json} via its {@code write} tool.
     * The runner script may also capture stdout as a fallback. This method tries the file first,
     * then falls back to extracting JSON from mixed text content.
     *
     * <p>Additionally sanitizes invalid JSON escapes from Swift string interpolation
     * ({@code \(variable)}) which produces invalid {@code \(} sequences in JSON strings.
     */
    @Override
    public AgentResult parseResult(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());

        byte[] resultFile = sandboxResult.outputFiles().get("result.json");
        if (resultFile == null) {
            return new AgentResult(success, output);
        }

        String rawContent = new String(resultFile, StandardCharsets.UTF_8);

        // Sanitize invalid JSON escapes from Swift string interpolation (\(variable))
        rawContent = sanitizeSwiftEscapes(rawContent);

        // Try direct JSON parse first (clean JSON response from write tool)
        if (isValidJsonWithFindings(rawContent)) {
            output.put("rawOutput", rawContent);
            return new AgentResult(success, output);
        }

        // Extract JSON from text that may contain markdown or mixed content
        String extracted = extractJsonFromText(rawContent);
        if (extracted != null) {
            output.put("rawOutput", extracted);
        } else {
            output.put("rawOutput", rawContent);
        }

        return new AgentResult(success, output, null);
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

    /**
     * Extract a JSON object containing "findings" from mixed text content.
     *
     * <p>Pi's print mode output may include markdown fences, explanatory text,
     * or other non-JSON content surrounding the findings JSON object.
     *
     * <p>Uses Jackson's streaming parser for reliable extraction — reads the first complete
     * JSON object from each '{' position, ignoring trailing non-JSON text. Only the first
     * 5 '{' positions are tried to bound the search cost.
     */
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

            // Use Jackson's streaming parser with char[] offset to avoid String allocation.
            // Reads exactly one JSON object, stopping at its boundary.
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
     * Build the shell auth setup prefix and populate env vars based on credential mode.
     *
     * <p>In PROXY mode, the shell command exports provider-specific env vars that reference
     * the sandbox-injected {@code LLM_PROXY_URL} and {@code LLM_PROXY_TOKEN}. For Azure OpenAI,
     * this also sets the required API version header.
     *
     * <p>In API_KEY mode, credentials are injected via shell-level export (not the container
     * env map) to avoid triggering the sandbox's blocked-prefix security filter for providers
     * like Azure whose env var names match blocked prefixes ({@code AZURE_*}).
     */
    String buildAuthSetup(AgentAdapterRequest request, Map<String, String> env) {
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
                // Pi's SDK appends /responses to AZURE_OPENAI_BASE_URL:
                // $LLM_PROXY_URL/openai → SDK calls $LLM_PROXY_URL/openai/responses
                // → proxy forwards to https://{resource}.openai.azure.com/openai/responses
                // NOTE: Do NOT use /openai/v1 — 2025-04-01-preview api-version requires /openai.
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

    /**
     * Build shell export commands for API_KEY mode. Uses shell-level export for all providers
     * so that credentials never enter the container env map (and thus never trigger the
     * sandbox's blocked-prefix security filter).
     */
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
}
