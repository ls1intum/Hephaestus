package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses structured agent output into validated practice findings. The MR summary is composed
 * server-side by {@link DeliveryComposer}; the agent only supplies findings and per-finding
 * inline diff suggestions.
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
 *       "verdict": "OBSERVED",
 *       "severity": "INFO",
 *       "confidence": 0.95,
 *       "evidence": { ... },
 *       "reasoning": "...",
 *       "guidance": "...",
 *       "suggestedDiffNotes": [
 *         { "filePath": "src/Foo.swift", "startLine": 10, "body": "Suggestion..." }
 *       ]
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

    /** Maximum length for the pre-rendered MR/PR summary note (matches PullRequestCommentPoster.MAX_BODY_LENGTH). */
    static final int MAX_MR_NOTE_LENGTH = 60_000;

    /** Maximum length for a single diff note body. */
    static final int MAX_DIFF_NOTE_BODY_LENGTH = 2_000;

    /** Maximum number of inline delivery notes per job. This bounds comment API fan-out, not finding detection. */
    static final int MAX_DELIVERY_DIFF_NOTES = 30;

    private final JsonMapper objectMapper;
    private final JsonMapper lenientMapper;

    public PracticeDetectionResultParser(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Lenient mapper for agent output: LLMs produce JSON with literal newlines/tabs/control
        // chars inside string values that strict JSON rejects.
        this.lenientMapper = objectMapper.rebuild().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
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
        JsonNode rawOutputNode = jobOutput.get("rawOutput");
        if (rawOutputNode == null || rawOutputNode.isNull() || rawOutputNode.isMissingNode()) {
            return ParseResult.empty("missing rawOutput field in job output");
        }
        String rawOutputText = rawOutputNode.asString();
        if (rawOutputText.isBlank()) {
            return ParseResult.empty("rawOutput is blank");
        }

        // rawOutput is JSON but LLMs sometimes emit Swift-style \(var) interpolation that strict
        // JSON rejects; sanitize then fall back to extracting JSON from mixed-text output.
        String sanitizedText = sanitizeJsonEscapes(rawOutputText);
        JsonNode root;
        try {
            root = lenientMapper.readTree(sanitizedText);
        } catch (JacksonException e) {
            root = extractJsonFromText(sanitizedText);
            if (root == null) {
                return ParseResult.empty("invalid JSON in rawOutput: " + e.getMessage());
            }
        }
        if (root == null || root.isNull()) {
            return ParseResult.empty("rawOutput parsed to null");
        }
        JsonNode findingsNode = extractFindingsNode(root);
        if (findingsNode == null || !findingsNode.isArray()) {
            return ParseResult.empty("missing or non-array 'findings' field");
        }
        if (findingsNode.isEmpty()) {
            return ParseResult.empty("findings array is empty");
        }

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

    private JsonNode extractFindingsNode(JsonNode root) {
        return root.get("findings");
    }

    /** Parse the {@code suggestedDiffNotes} array on a single finding (may be absent). */
    private List<DiffNote> parseSuggestedDiffNotes(JsonNode entry, int findingIndex) {
        JsonNode suggestedNode = entry.get("suggestedDiffNotes");
        if (suggestedNode == null || suggestedNode.isNull() || !suggestedNode.isArray()) {
            return List.of();
        }
        List<DiffNote> notes = new ArrayList<>();
        for (int j = 0; j < suggestedNode.size(); j++) {
            DiffNote note = parseSingleDiffNote(suggestedNode.get(j), findingIndex, j);
            if (note != null) {
                notes.add(note);
            }
        }
        return Collections.unmodifiableList(notes);
    }

    /**
     * Parse a single diff note JSON object. Returns null if the entry is invalid.
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
            !filePathNode.isString() ||
            filePathNode.asString().isBlank()
        ) {
            log.debug("Skipping suggestedDiffNote at finding {}, index {}: missing filePath", findingIndex, noteIndex);
            return null;
        }
        String filePath = filePathNode.asString();

        // Reject internal workspace paths — agent sometimes hallucinates inputs/context/ or work/analysis/ paths
        if (
            filePath.startsWith(de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.CONTEXT_PREFIX) ||
            filePath.startsWith(de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRACTICES_PREFIX) ||
            filePath.startsWith(de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.ANALYSIS_PREFIX) ||
            filePath.startsWith("out/") ||
            filePath.startsWith(de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_PREFIX) ||
            filePath.startsWith(de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_OUT_PREFIX)
        ) {
            log.debug(
                "Skipping suggestedDiffNote with internal path at finding {}, index {}: {}",
                findingIndex,
                noteIndex,
                filePath
            );
            return null;
        }

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
        if (bodyNode == null || bodyNode.isNull() || !bodyNode.isString() || bodyNode.asString().isBlank()) {
            log.debug("Skipping suggestedDiffNote at finding {}, index {}: missing body", findingIndex, noteIndex);
            return null;
        }
        String body = bodyNode.asString();
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

    // Finding entry validation

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

        // Required: verdict (accepts the legacy POSITIVE/NEGATIVE vocabulary for in-flight agents)
        Verdict verdict = parseVerdict(entry);

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
            } catch (JacksonException e) {
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

        // Optional: per-finding suggested diff notes — the agent's inline-comment suggestions.
        List<DiffNote> suggestedDiffNotes = parseSuggestedDiffNotes(entry, index);

        return new ValidatedFinding(
            practiceSlug,
            title,
            verdict,
            severity,
            confidence,
            evidence,
            reasoning,
            guidance,
            suggestedDiffNotes
        );
    }

    private static String textField(JsonNode entry, String field) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isString()) {
            throw new EntryValidationException("missing or non-text field: " + field);
        }
        return node.asString();
    }

    private static String optionalTextField(JsonNode entry, String field) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isString()) {
            return null;
        }
        String text = node.asString();
        return text.isBlank() ? null : text;
    }

    /**
     * Parses the sign-neutral {@link Verdict}, tolerating the legacy POSITIVE/NEGATIVE vocabulary so an
     * agent still emitting the old enum mid-rollout maps cleanly onto the renamed values (ADR 0021, F-6).
     * POSITIVE → OBSERVED, NEGATIVE → NOT_OBSERVED; NOT_APPLICABLE is unchanged.
     */
    private static Verdict parseVerdict(JsonNode entry) {
        JsonNode node = entry.get("verdict");
        if (node == null || node.isNull() || !node.isString()) {
            throw new EntryValidationException("missing or non-text field: verdict");
        }
        String raw = node.asString().trim().toUpperCase(Locale.ROOT);
        switch (raw) {
            case "POSITIVE":
                return Verdict.OBSERVED;
            case "NEGATIVE":
                return Verdict.NOT_OBSERVED;
            default:
                break;
        }
        try {
            return Verdict.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new EntryValidationException("invalid verdict value: '" + node.asString() + "'");
        }
    }

    private static <E extends Enum<E>> E parseEnum(JsonNode entry, String field, Class<E> enumType) {
        JsonNode node = entry.get(field);
        if (node == null || node.isNull() || !node.isString()) {
            throw new EntryValidationException("missing or non-text field: " + field);
        }
        try {
            return Enum.valueOf(enumType, node.asString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EntryValidationException("invalid " + field + " value: '" + node.asString() + "'");
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
                JsonNode node = lenientMapper.readTree(text.substring(braceIdx));
                if (node != null && node.isObject() && node.has("findings")) {
                    return node;
                }
            } catch (JacksonException ignored) {
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

    // Result types

    /**
     * @param validFindings validated findings from the agent output
     * @param discarded entries that failed validation with reasons
     */
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
        List<DiffNote> suggestedDiffNotes
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
