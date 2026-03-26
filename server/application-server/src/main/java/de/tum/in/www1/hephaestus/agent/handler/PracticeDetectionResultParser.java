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
import org.springframework.lang.Nullable;

/**
 * Parses structured agent output into validated practice findings and optional delivery content.
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
 *   ],
 *   "delivery": {
 *     "mrNote": "Markdown summary for PR author...",
 *     "diffNotes": [
 *       { "filePath": "src/Foo.java", "startLine": 10, "body": "Suggestion..." }
 *     ]
 *   }
 * }
 * }</pre>
 */
public class PracticeDetectionResultParser {

    private static final Logger log = LoggerFactory.getLogger(PracticeDetectionResultParser.class);

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_REASONING_LENGTH = 10_000;
    private static final int MAX_GUIDANCE_LENGTH = 5_000;
    private static final int MAX_EVIDENCE_BYTES = 64 * 1024;

    /** Maximum length for the pre-rendered MR/PR summary note (matches PullRequestCommentPoster.MAX_BODY_LENGTH). */
    static final int MAX_MR_NOTE_LENGTH = 60_000;

    /** Maximum length for a single diff note body. */
    static final int MAX_DIFF_NOTE_BODY_LENGTH = 2_000;

    /** Maximum number of diff notes per job. Bounds API calls for GitLab (one per note). */
    static final int MAX_DIFF_NOTES = 10;

    private final ObjectMapper objectMapper;
    private final int maxFindingsPerJob;

    public PracticeDetectionResultParser(ObjectMapper objectMapper, int maxFindingsPerJob) {
        this.objectMapper = objectMapper;
        this.maxFindingsPerJob = maxFindingsPerJob;
    }

    /**
     * Parse agent output into validated findings and optional delivery content. Never throws.
     *
     * @param jobOutput the {@code AgentJob.output} JSONB node (contains {@code rawOutput} string)
     * @return parse result with valid findings, discarded entries, and optional delivery content
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

        // Step 6: Extract optional delivery content (never affects findings)
        DeliveryContent delivery = parseDeliveryContent(root);

        return new ParseResult(Collections.unmodifiableList(valid), Collections.unmodifiableList(discarded), delivery);
    }

    // =========================================================================
    // Delivery content parsing
    // =========================================================================

    @Nullable
    private DeliveryContent parseDeliveryContent(JsonNode root) {
        JsonNode deliveryNode = root.get("delivery");
        if (deliveryNode == null || deliveryNode.isNull() || deliveryNode.isMissingNode()) {
            return null;
        }
        if (!deliveryNode.isObject()) {
            log.debug("delivery field is not an object, ignoring");
            return null;
        }

        // mrNote — optional text
        String mrNote = null;
        JsonNode mrNoteNode = deliveryNode.get("mrNote");
        if (mrNoteNode != null && !mrNoteNode.isNull() && mrNoteNode.isTextual()) {
            mrNote = mrNoteNode.asText();
            if (mrNote.isBlank()) {
                mrNote = null;
            } else if (mrNote.length() > MAX_MR_NOTE_LENGTH) {
                log.debug("Truncating mrNote from {} to {} chars", mrNote.length(), MAX_MR_NOTE_LENGTH);
                mrNote = mrNote.substring(0, MAX_MR_NOTE_LENGTH);
            }
        }

        // diffNotes — optional array
        List<DiffNote> diffNotes = parseDiffNotes(deliveryNode);

        if (mrNote == null && diffNotes.isEmpty()) {
            return null;
        }

        return new DeliveryContent(mrNote, diffNotes);
    }

    private List<DiffNote> parseDiffNotes(JsonNode deliveryNode) {
        JsonNode diffNotesNode = deliveryNode.get("diffNotes");
        if (diffNotesNode == null || diffNotesNode.isNull() || !diffNotesNode.isArray()) {
            return List.of();
        }

        List<DiffNote> notes = new ArrayList<>();
        int limit = Math.min(diffNotesNode.size(), MAX_DIFF_NOTES);

        if (diffNotesNode.size() > MAX_DIFF_NOTES) {
            log.debug("Capping diffNotes from {} to {}", diffNotesNode.size(), MAX_DIFF_NOTES);
        }

        for (int i = 0; i < limit; i++) {
            JsonNode entry = diffNotesNode.get(i);
            if (!entry.isObject()) {
                log.debug("Skipping non-object diffNote at index {}", i);
                continue;
            }

            // Required: filePath
            JsonNode filePathNode = entry.get("filePath");
            if (
                filePathNode == null ||
                filePathNode.isNull() ||
                !filePathNode.isTextual() ||
                filePathNode.asText().isBlank()
            ) {
                log.debug("Skipping diffNote at index {}: missing filePath", i);
                continue;
            }
            String filePath = filePathNode.asText();

            // Required: startLine (positive integer)
            JsonNode startLineNode = entry.get("startLine");
            if (startLineNode == null || startLineNode.isNull() || !startLineNode.isNumber()) {
                log.debug("Skipping diffNote at index {}: missing or non-numeric startLine", i);
                continue;
            }
            int startLine = startLineNode.asInt();
            if (startLine <= 0) {
                log.debug("Skipping diffNote at index {}: startLine must be positive, got {}", i, startLine);
                continue;
            }

            // Optional: endLine (positive integer, must be >= startLine)
            Integer endLine = null;
            JsonNode endLineNode = entry.get("endLine");
            if (endLineNode != null && !endLineNode.isNull() && endLineNode.isNumber()) {
                int endLineValue = endLineNode.asInt();
                if (endLineValue >= startLine) {
                    endLine = endLineValue;
                }
            }

            // Required: body
            JsonNode bodyNode = entry.get("body");
            if (bodyNode == null || bodyNode.isNull() || !bodyNode.isTextual() || bodyNode.asText().isBlank()) {
                log.debug("Skipping diffNote at index {}: missing body", i);
                continue;
            }
            String body = bodyNode.asText();
            if (body.length() > MAX_DIFF_NOTE_BODY_LENGTH) {
                log.debug(
                    "Truncating diffNote body from {} to {} chars at index {}",
                    body.length(),
                    MAX_DIFF_NOTE_BODY_LENGTH,
                    i
                );
                body = body.substring(0, MAX_DIFF_NOTE_BODY_LENGTH);
            }

            notes.add(new DiffNote(filePath, startLine, endLine, body));
        }

        return Collections.unmodifiableList(notes);
    }

    // =========================================================================
    // Finding entry validation
    // =========================================================================

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

    /**
     * @param validFindings validated findings from the agent output
     * @param discarded entries that failed validation with reasons
     * @param delivery optional pre-rendered delivery content (null if absent or malformed)
     */
    public record ParseResult(
        List<ValidatedFinding> validFindings,
        List<DiscardedEntry> discarded,
        @Nullable DeliveryContent delivery
    ) {
        static ParseResult empty(String reason) {
            return new ParseResult(List.of(), List.of(new DiscardedEntry(-1, reason)), null);
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

    /**
     * Pre-rendered delivery content from the agent. The agent produces this alongside
     * structured findings — the server sanitizes and posts it without further rendering.
     *
     * @param mrNote  markdown summary for the PR/MR comment (null if agent didn't produce one)
     * @param diffNotes inline diff comments with file locations
     */
    public record DeliveryContent(@Nullable String mrNote, List<DiffNote> diffNotes) {}

    /**
     * An inline diff note targeting a specific file and line range.
     *
     * @param filePath  path relative to repo root (new path, not old)
     * @param startLine first line number (1-based, must be positive)
     * @param endLine   optional last line number for multi-line (GitHub only; GitLab ignores)
     * @param body      markdown comment body (sanitized before posting)
     */
    public record DiffNote(String filePath, int startLine, @Nullable Integer endLine, String body) {}
}
