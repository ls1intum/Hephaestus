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
    private static final String EFFORT_LEVEL = "max";

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

        // Initialize a git repo from injected source files so the agent can use git commands.
        // Creates two branches: 'target' (base) and 'pr' (head, checked out).
        // The target branch is reconstructed by reverse-applying the diff patch.
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
            " && ";

        // Full agentic mode with optimal flags:
        // --json-schema: constrained decoding for reliable structured output (post-agentic-loop)
        // --effort max: maximum extended thinking for thorough code review
        // --no-session-persistence: skip disk I/O in ephemeral containers
        // --max-budget-usd: cost ceiling to prevent runaway spending
        // --verbose: debug info on stderr (does not affect stdout)
        String command =
            authSetup +
            gitSetup +
            "mkdir -p " +
            OUTPUT_PATH +
            " && PROMPT=$(cat /workspace/.prompt)" +
            " && SCHEMA=$(cat /workspace/.json-schema)" +
            " && claude -p \"$PROMPT\" --output-format json" +
            " --json-schema \"$SCHEMA\"" +
            " --effort " +
            EFFORT_LEVEL +
            " --dangerously-skip-permissions" +
            " --no-session-persistence" +
            " --max-budget-usd " +
            MAX_BUDGET_USD +
            " --verbose" +
            " > " +
            OUTPUT_PATH +
            "/result.json";

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
            "verdict":{"type":"string","enum":["POSITIVE","NEGATIVE","NOT_APPLICABLE","NEEDS_REVIEW"]},\
            "severity":{"type":"string","enum":["CRITICAL","MAJOR","MINOR","INFO"]},\
            "confidence":{"type":"number","minimum":0,"maximum":1},\
            "evidence":{"type":"object"},"reasoning":{"type":"string"},\
            "guidance":{"type":"string"},"guidanceMethod":{"type":"string",\
            "enum":["MODELING","COACHING","SCAFFOLDING","ARTICULATION","REFLECTION","EXPLORATION"]}},\
            "required":["practiceSlug","title","verdict","severity","confidence"]}},\
            "delivery":{"type":"object","properties":{"mrNote":{"type":"string"},\
            "diffNotes":{"type":"array","items":{"type":"object","properties":{\
            "filePath":{"type":"string"},"startLine":{"type":"integer","minimum":1},\
            "endLine":{"type":"integer","minimum":1},"body":{"type":"string"}},\
            "required":["filePath","startLine","body"]}}}}},"required":["findings"]}""";
        return schema.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse the Claude Code CLI output and extract the model's structured response.
     *
     * <p>The CLI outputs a JSON array of events: {@code [{type:"system",...}, {type:"assistant",...},
     * {type:"result",...}]}. With {@code --json-schema}, the result event may contain a
     * {@code structured_output} field with validated JSON. Falls back to extracting from
     * the {@code result} text field.
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

        return new AgentResult(success, output);
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
