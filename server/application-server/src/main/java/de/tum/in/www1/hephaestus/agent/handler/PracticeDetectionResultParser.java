package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses structured agent output into validated practice findings.
 *
 * <p>This is a pure function with no Spring dependencies. It never throws — all
 * parse failures are captured in {@link ParseResult#discarded()}.
 *
 * <p>Expected input shape (stored as escaped JSON string at {@code jobOutput.rawOutput}):
 * <pre>{@code
 * {
 *   "findings": [
 *     {
 *       "practiceSlug": "pr-description-quality",
 *       "title": "Good PR description",
 *       "verdict": "POSITIVE",
 *       "severity": "INFO",
 *       "confidence": 0.95,
 *       "evidence": { ... },
 *       "reasoning": "...",
 *       "guidance": "...",
 *       "guidanceMethod": "COACHING"
 *     }
 *   ]
 * }
 * }</pre>
 */
public class PracticeDetectionResultParser {

    private static final Logger log = LoggerFactory.getLogger(PracticeDetectionResultParser.class);

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_REASONING_LENGTH = 10_000;
    private static final int MAX_GUIDANCE_LENGTH = 5_000;
    private static final int MAX_EVIDENCE_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final int maxFindingsPerJob;

    public PracticeDetectionResultParser(ObjectMapper objectMapper, int maxFindingsPerJob) {
        this.objectMapper = objectMapper;
        this.maxFindingsPerJob = maxFindingsPerJob;
    }

    /**
     * Parse agent output into validated findings. Never throws.
     *
     * @param jobOutput the {@code AgentJob.output} JSONB node (contains {@code rawOutput} string)
     * @return parse result with valid findings and discarded entries
     */
    public ParseResult parse(JsonNode jobOutput) {
        if (jobOutput == null || jobOutput.isNull() || jobOutput.isMissingNode()) {
            return ParseResult.empty("jobOutput is null or missing");
        }

        // Step 1: Extract rawOutput string
        JsonNode rawOutputNode = jobOutput.get("rawOutput");
        if (rawOutputNode == null || rawOutputNode.isNull() || rawOutputNode.isMissingNode()) {
            return ParseResult.empty("missing rawOutput field in job output");
        }
        String rawOutputText = rawOutputNode.asText();
        if (rawOutputText.isBlank()) {
            return ParseResult.empty("rawOutput is blank");
        }

        // Step 2: Double-parse the escaped JSON string
        JsonNode root;
        try {
            root = objectMapper.readTree(rawOutputText);
        } catch (JsonProcessingException e) {
            return ParseResult.empty("invalid JSON in rawOutput: " + e.getMessage());
        }
        if (root == null || root.isNull()) {
            return ParseResult.empty("rawOutput parsed to null");
        }

        // Step 3: Extract findings array
        JsonNode findingsNode = root.get("findings");
        if (findingsNode == null || !findingsNode.isArray()) {
            return ParseResult.empty("missing or non-array 'findings' field");
        }
        if (findingsNode.isEmpty()) {
            return ParseResult.empty("findings array is empty");
        }

        // Step 4: Max findings guard
        if (findingsNode.size() > maxFindingsPerJob) {
            return ParseResult.empty("findings count " + findingsNode.size() + " exceeds max " + maxFindingsPerJob);
        }

        // Step 5: Validate each entry
        List<ValidatedFinding> valid = new ArrayList<>();
        List<DiscardedEntry> discarded = new ArrayList<>();

        for (int i = 0; i < findingsNode.size(); i++) {
            JsonNode entry = findingsNode.get(i);
            if (!entry.isObject()) {
                discarded.add(new DiscardedEntry(i, "entry is not a JSON object"));
                continue;
            }
            try {
                valid.add(validateEntry(entry, i));
            } catch (EntryValidationException e) {
                discarded.add(new DiscardedEntry(i, e.getMessage()));
            }
        }

        return new ParseResult(Collections.unmodifiableList(valid), Collections.unmodifiableList(discarded));
    }

    private ValidatedFinding validateEntry(JsonNode entry, int index) {
        // Required: practiceSlug
        String practiceSlug = textField(entry, "practiceSlug");
        if (practiceSlug.isBlank()) {
            throw new EntryValidationException("practiceSlug is blank");
        }
        practiceSlug = practiceSlug.toLowerCase(Locale.ROOT).replace('_', '-');

        // Required: title
        String title = textField(entry, "title");
        if (title.isBlank()) {
            throw new EntryValidationException("title is blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH - 3) + "...";
        }

        // Required: verdict
        Verdict verdict = parseEnum(entry, "verdict", Verdict.class);

        // Required: severity
        Severity severity = parseEnum(entry, "severity", Severity.class);

        // Required: confidence
        float confidence = parseConfidence(entry);

        // Optional: evidence
        JsonNode evidence = null;
        JsonNode evidenceNode = entry.get("evidence");
        if (evidenceNode != null && !evidenceNode.isNull() && !evidenceNode.isMissingNode()) {
            try {
                String serialized = objectMapper.writeValueAsString(evidenceNode);
                if (serialized.getBytes(StandardCharsets.UTF_8).length <= MAX_EVIDENCE_BYTES) {
                    evidence = evidenceNode;
                } else {
                    log.debug("Evidence exceeds {} bytes, dropping: slug={}", MAX_EVIDENCE_BYTES, practiceSlug);
                }
            } catch (JsonProcessingException e) {
                // Silently drop unparseable evidence
            }
        }

        // Optional: reasoning
        String reasoning = optionalTextField(entry, "reasoning");
        if (reasoning != null && reasoning.length() > MAX_REASONING_LENGTH) {
            log.debug(
                "Truncating reasoning from {} to {} chars: slug={}",
                reasoning.length(),
                MAX_REASONING_LENGTH,
                practiceSlug
            );
            reasoning = reasoning.substring(0, MAX_REASONING_LENGTH);
        }

        // Optional: guidance
        String guidance = optionalTextField(entry, "guidance");
        if (guidance != null && guidance.length() > MAX_GUIDANCE_LENGTH) {
            log.debug(
                "Truncating guidance from {} to {} chars: slug={}",
                guidance.length(),
                MAX_GUIDANCE_LENGTH,
                practiceSlug
            );
            guidance = guidance.substring(0, MAX_GUIDANCE_LENGTH);
        }

        // Optional: guidanceMethod
        CaMethod guidanceMethod = null;
        JsonNode gmNode = entry.get("guidanceMethod");
        if (gmNode != null && !gmNode.isNull() && gmNode.isTextual() && !gmNode.asText().isBlank()) {
            try {
                guidanceMethod = CaMethod.valueOf(gmNode.asText().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // Invalid guidance method — leave null, don't discard the entire finding
            }
        }

        return new ValidatedFinding(
            practiceSlug,
            title,
            verdict,
            severity,
            confidence,
            evidence,
            reasoning,
            guidance,
            guidanceMethod
        );
    }

    private static String textField(JsonNode entry, String field) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new EntryValidationException("missing or non-text field: " + field);
        }
        return node.asText();
    }

    private static String optionalTextField(JsonNode entry, String field) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private static <E extends Enum<E>> E parseEnum(JsonNode entry, String field, Class<E> enumType) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new EntryValidationException("missing or non-text field: " + field);
        }
        try {
            return Enum.valueOf(enumType, node.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EntryValidationException("invalid " + field + " value: '" + node.asText() + "'");
        }
    }

    private static float parseConfidence(JsonNode entry) {
        JsonNode node = entry.get("confidence");
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new EntryValidationException("missing or non-numeric confidence");
        }
        float confidence = node.floatValue();
        // Detect percentage (e.g., 85 → 0.85) — values >1 and ≤100 treated as percentages
        if (confidence > 1.0f && confidence <= 100.0f) {
            confidence = confidence / 100.0f;
        }
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new EntryValidationException("confidence out of range [0.0, 1.0]: " + confidence);
        }
        return confidence;
    }

    private static class EntryValidationException extends RuntimeException {

        EntryValidationException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this; // Flow-control exception — skip expensive stack trace capture
        }
    }

    // =========================================================================
    // Result types
    // =========================================================================

    public record ParseResult(List<ValidatedFinding> validFindings, List<DiscardedEntry> discarded) {
        static ParseResult empty(String reason) {
            return new ParseResult(List.of(), List.of(new DiscardedEntry(-1, reason)));
        }
    }

    public record ValidatedFinding(
        String practiceSlug,
        String title,
        Verdict verdict,
        Severity severity,
        float confidence,
        JsonNode evidence,
        String reasoning,
        String guidance,
        CaMethod guidanceMethod
    ) {}

    public record DiscardedEntry(int index, String reason) {}
}
