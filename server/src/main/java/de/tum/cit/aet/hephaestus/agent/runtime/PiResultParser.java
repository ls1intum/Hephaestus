package de.tum.cit.aet.hephaestus.agent.runtime;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parse Pi-runner output into an {@link AgentResult}.
 *
 * <p>Handles {@code result.json}, falls back to {@code review-state.json}, and surfaces
 * {@code usage.json}, {@code runner-debug.json}, {@code watchdog-killed.json}. Sanitises
 * Swift {@code \(...)} interpolation that produces invalid JSON.
 *
 * <p>Parse failures are non-fatal (best-effort) and counted by the
 * {@code agent.pi.result.parse.failure{stage}} counter.
 */
@Service
public class PiResultParser {

    private static final Logger log = LoggerFactory.getLogger(PiResultParser.class);
    private static final int MAX_BRACE_ATTEMPTS = 5;
    private static final String METRIC_PARSE_FAILURE = "agent.pi.result.parse.failure";

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public PiResultParser(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** Parse the sandbox result, falling back to {@code review-state.json} when {@code result.json} is absent. */
    public AgentResult parse(SandboxResult sandboxResult) {
        boolean success = sandboxResult.exitCode() == 0 && !sandboxResult.timedOut();
        Map<String, Object> output = new HashMap<>();
        output.put("exitCode", sandboxResult.exitCode());
        output.put("timedOut", sandboxResult.timedOut());
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
        if (isValidJsonWithObservations(rawContent)) {
            output.put("rawOutput", rawContent);
            return new AgentResult(success, output, usage);
        }

        String extracted = extractJsonFromText(rawContent);
        output.put("rawOutput", extracted != null ? extracted : rawContent);
        return new AgentResult(success, output, usage);
    }

    AgentResult.LlmUsage parseUsage(byte[] usageFile) {
        if (usageFile == null || usageFile.length == 0) {
            return null;
        }
        try {
            JsonNode usageNode = objectMapper.readTree(usageFile);
            int totalCalls = usageNode.path("totalCalls").asInt(0);
            if (totalCalls <= 0) {
                return null;
            }
            String model = usageNode.path("model").isString() ? usageNode.path("model").asString() : null;
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
        } catch (JacksonException e) {
            recordFailure("usage", e);
            return null;
        }
    }

    void addRunnerDebug(Map<String, Object> output, byte[] runnerDebugFile) {
        if (runnerDebugFile == null || runnerDebugFile.length == 0) {
            return;
        }
        try {
            output.put("runnerDebug", objectMapper.readValue(runnerDebugFile, Object.class));
        } catch (JacksonException e) {
            recordFailure("runner_debug", e);
        }
    }

    void addWatchdogState(Map<String, Object> output, byte[] watchdogFile) {
        if (watchdogFile == null || watchdogFile.length == 0) {
            return;
        }
        try {
            output.put("watchdogKilled", objectMapper.readValue(watchdogFile, Object.class));
        } catch (JacksonException e) {
            recordFailure("watchdog", e);
        }
    }

    byte[] buildResultFromReviewState(byte[] reviewStateFile) {
        if (reviewStateFile == null || reviewStateFile.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(reviewStateFile);
            JsonNode observations = root.get("observations");
            if (observations == null || !observations.isArray() || observations.isEmpty()) {
                return null;
            }
            Map<String, Object> assembled = new LinkedHashMap<>();
            assembled.put("observations", objectMapper.treeToValue(observations, Object.class));
            return objectMapper.writeValueAsBytes(assembled);
        } catch (JacksonException e) {
            recordFailure("review_state", e);
            return null;
        }
    }

    /** Drop invalid JSON escapes produced when the LLM quotes Swift string interpolation. */
    String sanitizeSwiftEscapes(String json) {
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
                    sb.append(c);
                } else {
                    continue;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Find the first '{'…'}' object containing 'observations' (max {@value MAX_BRACE_ATTEMPTS} attempts). */
    String extractJsonFromText(String text) {
        int searchFrom = 0;
        char[] chars = text.toCharArray();
        int attempts = 0;
        while (searchFrom < chars.length && attempts < MAX_BRACE_ATTEMPTS) {
            int bracePos = text.indexOf('{', searchFrom);
            if (bracePos == -1) {
                break;
            }
            attempts++;
            try (
                var parser = objectMapper.tokenStreamFactory().createParser(chars, bracePos, chars.length - bracePos)
            ) {
                JsonNode node = objectMapper.readTree(parser);
                if (node != null && node.isObject() && node.has("observations")) {
                    return objectMapper.writeValueAsString(node);
                }
            } catch (JacksonException e) {
                log.trace("No JSON object at position {}: {}", bracePos, e.getMessage());
            }
            searchFrom = bracePos + 1;
        }
        return null;
    }

    boolean isValidJsonWithObservations(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            return node != null && node.isObject() && node.has("observations");
        } catch (JacksonException e) {
            return false;
        }
    }

    private void recordFailure(String stage, JacksonException e) {
        log.warn("Failed to parse Pi {} output: {}", stage, e.getMessage());
        meterRegistry.counter(METRIC_PARSE_FAILURE, Tags.of("stage", stage)).increment();
    }
}
