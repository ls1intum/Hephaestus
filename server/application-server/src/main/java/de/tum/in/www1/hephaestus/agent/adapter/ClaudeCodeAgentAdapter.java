package de.tum.in.www1.hephaestus.agent.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
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
 * Adapter for Anthropic's Claude Code CLI agent.
 *
 * <p>CLI: {@code claude -p "prompt" --output-format json --dangerously-skip-permissions}
 *
 * <p>Authentication modes:
 * <ul>
 *   <li>PROXY: bridges {@code $LLM_PROXY_URL} → {@code ANTHROPIC_BASE_URL}</li>
 *   <li>API_KEY: sets {@code ANTHROPIC_API_KEY} directly</li>
 *   <li>OAUTH: sets {@code CLAUDE_CODE_OAUTH_TOKEN} directly</li>
 * </ul>
 *
 * <p>Result parsing: The CLI outputs a JSON event array to stdout. This adapter extracts the
 * model's text from the {@code result} field of the last {@code type: "result"} event, then
 * finds the embedded JSON object (the practice detection output) within that text.
 */
public class ClaudeCodeAgentAdapter implements AgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeAgentAdapter.class);

    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-claude-code:latest";
    static final String OUTPUT_PATH = "/workspace/.output";
    private static final int MAX_BRACE_ATTEMPTS = 5;
    private static final String MAX_BUDGET_USD = "5.00";
    private static final String EFFORT_LEVEL = "medium";
    private static final int MAX_TURNS = 25;
    private static final int MAX_STDOUT_BUFFER_BYTES = 10 * 1024 * 1024;
    /** Buffer time (seconds) reserved for retries + cleanup before container timeout. */
    static final int TIMEOUT_BUFFER_SECONDS = 60;

    private final ObjectMapper objectMapper;

    ClaudeCodeAgentAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType agentType() {
        return AgentType.CLAUDE_CODE;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        Map<String, String> env = new HashMap<>();
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        String authSetup = buildAuthSetup(request, env);

        if (request.modelName() != null && !request.modelName().isBlank()) {
            env.put("ANTHROPIC_MODEL", request.modelName());
        }

        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));
        inputFiles.put(".json-schema", buildJsonSchema());

        // Claude Code-specific orchestrator file (auto-discovered by Claude Code CLI)
        inputFiles.put("CLAUDE.md", AgentAdapter.loadClasspathResource("CLAUDE.md"));

        // Node.js runner script handles: run claude → validate output → retry via --continue
        long agentTimeoutMs = Math.max(60_000L, (long) (request.timeoutSeconds() - TIMEOUT_BUFFER_SECONDS) * 1000);
        inputFiles.put(".run-claude.mjs", buildRunnerScript(agentTimeoutMs));

        String command =
            authSetup +
            "mkdir -p " +
            OUTPUT_PATH +
            " && " +
            AgentAdapter.buildPrecomputeStep() +
            "node /workspace/.run-claude.mjs" +
            "; cp -r /workspace/.analysis " +
            OUTPUT_PATH +
            "/analysis 2>/dev/null; true";

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
     * Build the JSON Schema for structured output validation.
     *
     * <p>When combined with {@code --output-format json}, Claude Code applies constrained
     * decoding after the agentic loop completes — the agent can use all tools freely during
     * analysis, then the final response is forced to conform to this schema. The validated
     * output appears in the {@code structured_output} field of the result JSON.
     */
    private byte[] buildJsonSchema() {
        String schema = """
            {"type":"object","properties":{"findings":{"type":"array","items":{"type":"object",\
            "properties":{"practiceSlug":{"type":"string"},"title":{"type":"string"},\
            "verdict":{"type":"string","enum":["POSITIVE","NEGATIVE","NOT_APPLICABLE"]},\
            "severity":{"type":"string","enum":["CRITICAL","MAJOR","MINOR","INFO"]},\
            "confidence":{"type":"number","minimum":0,"maximum":1},\
            "evidence":{"type":"object","properties":{\
            "locations":{"type":"array","items":{"type":"object","properties":{\
            "path":{"type":"string"},"startLine":{"type":"integer","minimum":1},\
            "endLine":{"type":"integer","minimum":1}}}},\
            "snippets":{"type":"array","items":{"type":"string"}}}},\
            "reasoning":{"type":"string"},\
            "guidance":{"type":"string"},\
            "suggestedDiffNotes":{"type":"array","items":{"type":"object","properties":{\
            "filePath":{"type":"string"},"startLine":{"type":"integer","minimum":1},\
            "endLine":{"type":"integer","minimum":1},"body":{"type":"string"}},\
            "required":["filePath","startLine","body"]}}},\
            "required":["practiceSlug","title","verdict","severity","confidence"]}},\
            "delivery":{"type":"object","properties":{\
            "mrNote":{"type":"string"}}}},"required":["findings"]}""";
        return schema.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build a Node.js runner script that invokes Claude Code with self-correction.
     *
     * <p>The script runs the initial analysis, validates the output for a {@code findings}
     * array, and if invalid, sends up to 2 correction messages via {@code --continue}
     * (which resumes the existing session with full context preserved).
     *
     * @param agentTimeoutMs total time budget for all runs (initial + retries)
     */
    private byte[] buildRunnerScript(long agentTimeoutMs) {
        // Reserve 60s per retry attempt from total timeout
        long retryTimeoutMs = 60_000;
        long initialTimeoutMs = Math.max(60_000, agentTimeoutMs - 2 * retryTimeoutMs);
        String script = """
            import { spawnSync } from 'child_process';
            import { readFileSync, writeFileSync, mkdirSync } from 'fs';

            const OUTPUT = '/workspace/.output';
            mkdirSync(OUTPUT, { recursive: true });

            const prompt = readFileSync('/workspace/.prompt', 'utf-8').trim();
            const schema = readFileSync('/workspace/.json-schema', 'utf-8').trim();

            function runClaude(args, label, timeoutMs) {
              console.error(`[run-claude] ${label}`);
              const start = Date.now();
              const r = spawnSync('claude', args, {
                encoding: 'utf-8',
                maxBuffer: %d,
                timeout: timeoutMs || 0,
                cwd: '/workspace',
                stdio: ['pipe', 'pipe', 'pipe']
              });
              const sec = ((Date.now() - start) / 1000).toFixed(1);
              console.error(`[run-claude] ${label}: ${sec}s, exit=${r.status}, stdout=${(r.stdout||'').length}b`);
              if (r.error) console.error(`[run-claude] ${label}: error=${r.error.message}`);
              return r;
            }

            function hasFindings(out) {
              if (!out?.trim()) return false;
              try {
                const p = JSON.parse(out);
                if (p?.findings?.length > 0) return true;
                if (Array.isArray(p)) {
                  for (const ev of p) {
                    if (ev.type === 'result') {
                      if (ev.structured_output?.findings?.length > 0) return true;
                      try { if (JSON.parse(ev.result)?.findings?.length > 0) return true; } catch {}
                      if (typeof ev.result === 'string' && ev.result.includes('"findings"')) return true;
                    }
                  }
                }
                if (p?.type === 'result') {
                  if (p.structured_output?.findings?.length > 0) return true;
                  try { if (JSON.parse(p.result)?.findings?.length > 0) return true; } catch {}
                }
              } catch {
                return out.includes('"findings"') && out.includes('"practiceSlug"');
              }
              return false;
            }

            // Initial run — session persists for --continue retries
            let result = runClaude([
              '-p', prompt,
              '--output-format', 'json',
              '--json-schema', schema,
              '--effort', '%s',
              '--max-turns', '%d',
              '--dangerously-skip-permissions',
              '--max-budget-usd', '%s',
              '--verbose'
            ], 'initial', %d);

            let bestOutput = result.stdout || '';
            writeFileSync(`${OUTPUT}/result.json`, bestOutput);
            writeFileSync(`${OUTPUT}/initial.json`, bestOutput);
            let valid = hasFindings(bestOutput);

            // Self-correction: up to 2 retries via --continue (same session, full context)
            for (let i = 1; i <= 2 && !valid; i++) {
              console.error(`[run-claude] retry ${i}/2: output invalid, correcting via --continue`);
              const msg = !bestOutput.trim()
                ? 'Your previous response was empty. Output a JSON object: {"findings": [{practiceSlug, title, verdict, severity, confidence, ...}, ...]}. Review the practices and diff, then produce findings.'
                : 'Your response could not be parsed as valid findings JSON. Output ONLY a JSON object: {"findings": [{practiceSlug, title, verdict (POSITIVE/NEGATIVE/NOT_APPLICABLE), severity (CRITICAL/MAJOR/MINOR/INFO), confidence (0-1), reasoning, guidance}, ...]}';

              result = runClaude([
                '--continue', '-p', msg,
                '--output-format', 'json',
                '--json-schema', schema,
                '--max-turns', '5',
                '--dangerously-skip-permissions',
                '--max-budget-usd', '1.00',
                '--verbose'
              ], `retry-${i}`, %d);

              const retryOutput = result.stdout || '';
              writeFileSync(`${OUTPUT}/retry-${i}.json`, retryOutput);
              if (hasFindings(retryOutput)) {
                bestOutput = retryOutput;
                valid = true;
              } else if (retryOutput.trim() && retryOutput.length > bestOutput.length) {
                bestOutput = retryOutput; // keep longer output for server-side parsing
              }
              // Always write best output to result.json (never overwrite with empty)
              writeFileSync(`${OUTPUT}/result.json`, bestOutput);
            }

            console.error(`[run-claude] ${valid ? 'SUCCESS' : 'FAILED: no valid findings after retries'}`);
            process.exit(valid ? 0 : (result.status || 1));
            """.formatted(
                MAX_STDOUT_BUFFER_BYTES,
                EFFORT_LEVEL,
                MAX_TURNS,
                MAX_BUDGET_USD,
                initialTimeoutMs,
                retryTimeoutMs
            );
        return script.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse the Claude Code CLI output and extract the model's structured response.
     *
     * <p>The CLI outputs a JSON array of events: {@code [{type:"system",...}, {type:"assistant",...},
     * {type:"result",...}]}. With {@code --json-schema}, the result event may contain a
     * {@code structured_output} field with validated JSON. Falls back to extracting from
     * the {@code result} text field.
     *
     * <p>Also extracts LLM usage statistics ({@code total_cost_usd}, {@code total_input_tokens},
     * {@code total_output_tokens}, {@code model}) from the {@code type: "result"} event.
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

        String cliOutput = new String(resultFile, StandardCharsets.UTF_8);
        String extracted = extractModelResponse(cliOutput);
        if (extracted != null) {
            output.put("rawOutput", extracted);
        } else {
            // Fallback: store raw CLI output for debugging
            output.put("rawOutput", cliOutput);
        }

        AgentResult.LlmUsage usage = extractUsage(cliOutput);
        if (usage != null) {
            log.info(
                "Claude Code usage: model={}, input={}, output={}, cost={}",
                usage.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.costUsd()
            );
        }

        return new AgentResult(success, output, usage);
    }

    /**
     * Extract the model's JSON response from the Claude Code CLI output.
     *
     * <p>Extraction priority:
     * <ol>
     *   <li>{@code structured_output} field (populated by {@code --json-schema})</li>
     *   <li>{@code result} field parsed as JSON</li>
     *   <li>{@code result} field with JSON extracted from mixed text</li>
     * </ol>
     *
     * <p>Handles both the JSON array format (current CLI output) and single-object
     * format (future CLI versions).
     */
    String extractModelResponse(String cliOutput) {
        try {
            JsonNode root = objectMapper.readTree(cliOutput);

            // Direct findings JSON (produced by --json-schema constrained decoding)
            if (root.isObject() && root.has("findings")) {
                return objectMapper.writeValueAsString(root);
            }

            // Single result object format
            if (root.isObject() && "result".equals(root.path("type").asText())) {
                return extractFromResultEvent(root);
            }

            // JSON array of events format
            if (root.isArray()) {
                for (JsonNode event : root) {
                    if ("result".equals(event.path("type").asText())) {
                        String extracted = extractFromResultEvent(event);
                        if (extracted != null) return extracted;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse Claude Code CLI output: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract LLM usage statistics from the Claude Code CLI output.
     *
     * <p>The {@code type: "result"} event contains top-level fields:
     * {@code total_cost_usd}, {@code total_input_tokens}, {@code total_output_tokens},
     * and {@code model}. These are extracted into an {@link AgentResult.LlmUsage} record.
     *
     * <p>Returns {@code null} when the output is a plain JSON object (e.g. direct
     * {@code --json-schema} structured output without an event wrapper) or when no
     * result event is found.
     *
     * @param cliOutput raw CLI stdout content
     * @return usage statistics, or null if unavailable
     */
    AgentResult.LlmUsage extractUsage(String cliOutput) {
        try {
            JsonNode root = objectMapper.readTree(cliOutput);
            JsonNode resultEvent = null;

            // Single result object format
            if (root.isObject() && "result".equals(root.path("type").asText())) {
                resultEvent = root;
            }

            // JSON array of events format — find the result event
            if (root.isArray()) {
                for (JsonNode event : root) {
                    if ("result".equals(event.path("type").asText())) {
                        resultEvent = event;
                        // Don't break — use the last result event if multiple exist
                    }
                }
            }

            if (resultEvent == null) {
                return null;
            }

            String model = resultEvent.path("model").asText(null);
            Integer inputTokens = intOrNull(resultEvent, "total_input_tokens");
            Integer outputTokens = intOrNull(resultEvent, "total_output_tokens");
            Double costUsd = doubleOrNull(resultEvent, "total_cost_usd");

            // Count assistant events as LLM calls (each represents one model invocation)
            int totalCalls = 0;
            if (root.isArray()) {
                for (JsonNode event : root) {
                    if ("assistant".equals(event.path("type").asText())) {
                        totalCalls++;
                    }
                }
            } else {
                // Single result object — at least one call was made
                totalCalls = 1;
            }

            // Only return usage if at least some data is present
            if (model == null && inputTokens == null && outputTokens == null && costUsd == null) {
                return null;
            }

            return new AgentResult.LlmUsage(
                model,
                inputTokens,
                outputTokens,
                null, // reasoningTokens — not reported by Claude Code CLI
                null, // cacheReadTokens — not reported by Claude Code CLI
                null, // cacheWriteTokens — not reported by Claude Code CLI
                costUsd,
                Math.max(totalCalls, 1)
            );
        } catch (Exception e) {
            log.warn("Failed to extract LLM usage from Claude Code output: {}", e.getMessage());
            return null;
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    /**
     * Extract findings JSON from a single result event node.
     * Prefers structured_output (from --json-schema), falls back to result text.
     */
    private String extractFromResultEvent(JsonNode event) throws Exception {
        // Priority 1: structured_output from --json-schema constrained decoding
        JsonNode structured = event.path("structured_output");
        if (!structured.isMissingNode() && structured.isObject() && structured.has("findings")) {
            return objectMapper.writeValueAsString(structured);
        }

        // Priority 2: result text field
        String resultText = event.path("result").asText(null);
        if (resultText == null || resultText.isBlank()) {
            return null;
        }

        // Try as-is (clean JSON response)
        if (isValidJsonWithFindings(resultText)) {
            return resultText;
        }

        // Extract JSON from text that may contain markdown, XML tags, or explanatory text
        return extractJsonFromText(resultText);
    }

    /**
     * Extract a JSON object containing "findings" from mixed text content.
     *
     * <p>The model may wrap JSON in XML-like tags (e.g. {@code <function_calls>}),
     * markdown code fences, or prefix it with explanatory text.
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

    /**
     * Build the shell auth setup prefix and populate env vars based on credential mode.
     */
    String buildAuthSetup(AgentAdapterRequest request, Map<String, String> env) {
        return switch (request.credentialMode()) {
            case PROXY -> "export ANTHROPIC_BASE_URL=\"$LLM_PROXY_URL\"" +
            " ANTHROPIC_API_KEY=\"$LLM_PROXY_TOKEN\"" +
            " ANTHROPIC_AUTH_TOKEN=''" +
            " CLAUDE_CODE_OAUTH_TOKEN=''" +
            " && ";
            case API_KEY -> {
                env.put("ANTHROPIC_API_KEY", request.credential());
                yield "export ANTHROPIC_AUTH_TOKEN=''" + " CLAUDE_CODE_OAUTH_TOKEN=''" + " && ";
            }
            case OAUTH -> {
                env.put("CLAUDE_CODE_OAUTH_TOKEN", request.credential());
                yield "export ANTHROPIC_API_KEY=''" + " ANTHROPIC_AUTH_TOKEN=''" + " && ";
            }
        };
    }
}
