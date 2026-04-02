package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *       "guidance": "..."
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
    static final int MAX_DIFF_NOTES = 30;

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

        // Step 2: Sanitize invalid JSON escapes (e.g., Swift's \(variable) interpolation)
        // then parse. The rawOutput may be pure JSON (from --json-schema) or may contain
        // phase markers followed by JSON (from orchestrator protocol).
        String sanitizedText = sanitizeJsonEscapes(rawOutputText);
        JsonNode root;
        try {
            root = objectMapper.readTree(sanitizedText);
        } catch (JsonProcessingException e) {
            // Fallback: try to extract JSON from mixed text (e.g., "[PHASE0]...\n{...}")
            root = extractJsonFromText(sanitizedText);
            if (root == null) {
                return ParseResult.empty("invalid JSON in rawOutput: " + e.getMessage());
            }
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

        // Step 4: Max findings guard — truncate rather than reject to preserve valid findings
        int findingsLimit = findingsNode.size();
        if (findingsLimit > maxFindingsPerJob) {
            log.warn("Truncating findings from {} to {} (maxFindingsPerJob)", findingsNode.size(), maxFindingsPerJob);
            findingsLimit = maxFindingsPerJob;
        }

        // Step 5: Validate each entry (respecting the truncation limit from Step 4)
        List<ValidatedFinding> valid = new ArrayList<>();
        List<DiscardedEntry> discarded = new ArrayList<>();

        for (int i = 0; i < findingsLimit; i++) {
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

        // Step 6: Deduplicate by practiceSlug — keep the finding with highest confidence
        List<ValidatedFinding> deduped = deduplicateByPracticeSlug(valid);
        if (deduped.size() < valid.size()) {
            log.info("Deduplicated findings: {} → {} (by practiceSlug)", valid.size(), deduped.size());
        }

        // Step 7: Extract optional delivery content (never affects findings)
        DeliveryContent delivery = parseDeliveryContent(root);

        // Step 8: Fallback — if delivery has no diffNotes, collect suggestedDiffNotes from
        // NEGATIVE findings' raw JSON. OpenCode subagents produce per-finding suggestedDiffNotes
        // that the orchestrator LLM often fails to aggregate into delivery.diffNotes.
        if (delivery == null || delivery.diffNotes().isEmpty()) {
            List<DiffNote> fallbackNotes = collectSuggestedDiffNotes(findingsNode, deduped);
            if (!fallbackNotes.isEmpty()) {
                String mrNote = delivery != null ? delivery.mrNote() : null;
                delivery = new DeliveryContent(mrNote, fallbackNotes);
                log.info(
                    "Collected {} diff notes from per-finding suggestedDiffNotes (fallback)",
                    fallbackNotes.size()
                );
            }
        }

        return new ParseResult(
            Collections.unmodifiableList(deduped),
            Collections.unmodifiableList(discarded),
            delivery
        );
    }

    // =========================================================================
    // Deduplication
    // =========================================================================

    /**
     * Deduplicate findings by practiceSlug, keeping the one with the highest confidence.
     * Agents sometimes produce multiple findings for the same practice; we keep only the best.
     */
    private static List<ValidatedFinding> deduplicateByPracticeSlug(List<ValidatedFinding> findings) {
        Map<String, ValidatedFinding> best = new LinkedHashMap<>();
        for (ValidatedFinding f : findings) {
            best.merge(f.practiceSlug(), f, (existing, incoming) ->
                incoming.confidence() > existing.confidence() ? incoming : existing
            );
        }
        return new ArrayList<>(best.values());
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

    /**
     * Fallback: collect suggestedDiffNotes from individual NEGATIVE findings when delivery.diffNotes is empty.
     *
     * <p>OpenCode subagents produce per-finding {@code suggestedDiffNotes} arrays, but the orchestrator
     * LLM often fails to aggregate them into {@code delivery.diffNotes}. This method scans raw finding
     * JSON nodes, matches them against validated NEGATIVE findings (by practiceSlug), and collects
     * their suggested diff notes — prioritizing higher severity findings, capped at {@link #MAX_DIFF_NOTES}.
     */
    private List<DiffNote> collectSuggestedDiffNotes(JsonNode findingsNode, List<ValidatedFinding> validatedFindings) {
        // Build lookup of NEGATIVE validated findings by slug → severity for filtering and sorting
        Map<String, Severity> negativeSlugs = new LinkedHashMap<>();
        for (ValidatedFinding vf : validatedFindings) {
            if (vf.verdict() == Verdict.NEGATIVE) {
                negativeSlugs.put(vf.practiceSlug(), vf.severity());
            }
        }
        if (negativeSlugs.isEmpty()) {
            return List.of();
        }

        // Collect (severity, diffNote) pairs from raw findings that match NEGATIVE validated slugs
        record ScoredNote(Severity severity, DiffNote note) {}
        List<ScoredNote> scored = new ArrayList<>();

        for (int i = 0; i < findingsNode.size(); i++) {
            JsonNode entry = findingsNode.get(i);
            if (!entry.isObject()) continue;

            // Match by practiceSlug — normalize the same way validateEntry does
            JsonNode slugNode = entry.get("practiceSlug");
            if (slugNode == null || slugNode.isNull() || !slugNode.isTextual()) continue;
            String slug = slugNode.asText().toLowerCase(Locale.ROOT).replace('_', '-');

            Severity severity = negativeSlugs.get(slug);
            if (severity == null) continue; // not a validated NEGATIVE finding

            // Parse suggestedDiffNotes array
            JsonNode suggestedNode = entry.get("suggestedDiffNotes");
            if (suggestedNode == null || suggestedNode.isNull() || !suggestedNode.isArray()) continue;

            for (int j = 0; j < suggestedNode.size(); j++) {
                JsonNode noteEntry = suggestedNode.get(j);
                DiffNote note = parseSingleDiffNote(noteEntry, i, j);
                if (note != null) {
                    scored.add(new ScoredNote(severity, note));
                }
            }
        }

        if (scored.isEmpty()) {
            return List.of();
        }

        // Sort by severity: CRITICAL (ordinal 0) first, INFO (ordinal 3) last
        scored.sort(Comparator.comparingInt(s -> s.severity().ordinal()));

        // Cap at MAX_DIFF_NOTES
        int limit = Math.min(scored.size(), MAX_DIFF_NOTES);
        List<DiffNote> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).note());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Parse a single diff note JSON object. Returns null if the entry is invalid.
     * Shared validation logic used by both delivery.diffNotes and suggestedDiffNotes parsing.
     */
    @Nullable
    private DiffNote parseSingleDiffNote(JsonNode entry, int findingIndex, int noteIndex) {
        if (!entry.isObject()) {
            log.debug("Skipping non-object suggestedDiffNote at finding {}, index {}", findingIndex, noteIndex);
            return null;
        }

        // Required: filePath
        JsonNode filePathNode = entry.get("filePath");
        if (
            filePathNode == null ||
            filePathNode.isNull() ||
            !filePathNode.isTextual() ||
            filePathNode.asText().isBlank()
        ) {
            log.debug("Skipping suggestedDiffNote at finding {}, index {}: missing filePath", findingIndex, noteIndex);
            return null;
        }
        String filePath = filePathNode.asText();

        // Required: startLine (positive integer)
        JsonNode startLineNode = entry.get("startLine");
        if (startLineNode == null || startLineNode.isNull() || !startLineNode.isNumber()) {
            log.debug(
                "Skipping suggestedDiffNote at finding {}, index {}: missing or non-numeric startLine",
                findingIndex,
                noteIndex
            );
            return null;
        }
        int startLine = startLineNode.asInt();
        if (startLine <= 0) {
            log.debug(
                "Skipping suggestedDiffNote at finding {}, index {}: startLine must be positive, got {}",
                findingIndex,
                noteIndex,
                startLine
            );
            return null;
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
            log.debug("Skipping suggestedDiffNote at finding {}, index {}: missing body", findingIndex, noteIndex);
            return null;
        }
        String body = bodyNode.asText();
        if (body.length() > MAX_DIFF_NOTE_BODY_LENGTH) {
            log.debug(
                "Truncating suggestedDiffNote body from {} to {} chars at finding {}, index {}",
                body.length(),
                MAX_DIFF_NOTE_BODY_LENGTH,
                findingIndex,
                noteIndex
            );
            body = body.substring(0, MAX_DIFF_NOTE_BODY_LENGTH);
        }

        return new DiffNote(filePath, startLine, endLine, body);
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
                log.debug("Failed to parse evidence JSON, dropping: slug={}, error={}", practiceSlug, e.getMessage());
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

        return new ValidatedFinding(practiceSlug, title, verdict, severity, confidence, evidence, reasoning, guidance);
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

    /**
     * Sanitize invalid JSON escape sequences commonly produced by LLMs generating
     * code snippets (e.g., Swift's {@code \(variable)} string interpolation).
     *
     * <p>In JSON, only {@code " \ / b f n r t u} are valid after a backslash.
     * This method doubles any backslash that precedes an invalid escape character,
     * turning {@code \(error)} into {@code \\(error)} which Jackson reads as a
     * literal backslash followed by {@code (error)}.
     */
    static String sanitizeJsonEscapes(String text) {
        // Quick check: if no backslash, nothing to fix
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 64);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (isValidJsonEscapeChar(next)) {
                    // Valid JSON escape — pass through both chars
                    sb.append(c);
                    sb.append(next);
                    i++; // skip next
                } else {
                    // Invalid JSON escape — double the backslash
                    sb.append('\\');
                    sb.append('\\');
                    // Don't skip next — it will be processed in the next iteration
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isValidJsonEscapeChar(char c) {
        return (
            c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r' || c == 't' || c == 'u'
        );
    }

    /**
     * Extract a JSON object containing "findings" from mixed text content.
     *
     * <p>The orchestrator protocol emits phase markers (e.g., {@code [PHASE0]...}) followed
     * by a JSON object. This method finds the first '{' that starts a valid JSON object
     * containing a "findings" array.
     */
    @Nullable
    private JsonNode extractJsonFromText(String text) {
        // Guard: skip absurdly large inputs (agent rawOutput shouldn't exceed 1MB)
        if (text.length() > 1_000_000) {
            log.warn("extractJsonFromText: input too large ({} chars), skipping", text.length());
            return null;
        }
        int startIdx = 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            int braceIdx = text.indexOf('{', startIdx);
            if (braceIdx < 0) break;
            try {
                JsonNode node = objectMapper.readTree(text.substring(braceIdx));
                if (node != null && node.isObject() && node.has("findings")) {
                    return node;
                }
            } catch (JsonProcessingException ignored) {
                // Not valid JSON from this position, try next '{'
            }
            startIdx = braceIdx + 1;
        }
        return null;
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
        String guidance
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
