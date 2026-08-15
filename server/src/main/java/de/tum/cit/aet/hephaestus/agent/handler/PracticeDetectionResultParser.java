package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 *       "presence": "PRESENT",
 *       "assessment": "GOOD",
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

    private static final int MAX_RAW_OUTPUT_LENGTH = 1_000_000;

    /**
     * Workspace-relative prefix of the collected-output dir, derived from the ABI's absolute {@code OUTPUT_PATH}
     * so the firewall below tracks a rename of the output dir instead of hardcoding {@code "out/"}.
     */
    private static final String OUTPUT_RELATIVE_PREFIX =
        SandboxLayout.OUTPUT_PATH.substring(SandboxLayout.WORKSPACE_ROOT.length() + 1) + "/";

    /** Matches {@code PullRequestCommentPoster.MAX_BODY_LENGTH}. */
    static final int MAX_MR_NOTE_LENGTH = 60_000;

    static final int MAX_DIFF_NOTE_BODY_LENGTH = 2_000;

    /**
     * The practices whose {@code BAD} finding may present as a merge-blocker ({@code CRITICAL}/{@code MAJOR}) —
     * ones that can break correctness, security, or data integrity. Every other (craft/process/authoring)
     * practice is ADVISORY: {@link ValidatedFinding#coerceCoherence(boolean, boolean)} caps its band to
     * {@code MINOR}. Confidence alone can't make this call — a craft critique and a real defect are emitted at
     * the same high confidence — so this consequence-class list is the discriminator. Pinned by
     * {@code PracticeDetectionResultParserTest}.
     */
    static final Set<String> BLOCKING_ELIGIBLE_PRACTICES = Set.of(
        "handles-errors-instead-of-swallowing-them",
        "validates-inputs-and-edge-cases-at-the-boundary",
        "avoids-unsafe-panics-and-chosen-crashes",
        "validates-and-escapes-untrusted-input",
        "avoids-insecure-defaults-and-over-broad-permissions",
        // A dishonest test (always-green, asserting nothing, disabled) HIDES defects rather than merely
        // missing them, so it keeps blocking weight despite being a "process" practice.
        "keeps-the-test-suite-honest"
    );

    /** Bounds comment API fan-out, not finding detection. */
    static final int MAX_DELIVERY_DIFF_NOTES = 30;

    private final JsonMapper objectMapper;
    private final JsonMapper lenientMapper;

    public PracticeDetectionResultParser(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
        // LLMs produce JSON with literal newlines/tabs/control chars inside string values that strict JSON rejects.
        this.lenientMapper = objectMapper.rebuild().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
    }

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
        // Bound the whole pipeline (readTree AND sanitizeJsonEscapes both walk the full string), not just the
        // fallback extractor — a runaway/oversized sandbox output must not be fully materialized in memory.
        if (rawOutputText.length() > MAX_RAW_OUTPUT_LENGTH) {
            log.warn("parse: rawOutput too large ({} chars), skipping", rawOutputText.length());
            return ParseResult.empty("rawOutput too large");
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

    @Nullable
    private DiffNote parseSingleDiffNote(JsonNode entry, int findingIndex, int noteIndex) {
        if (!entry.isObject()) {
            log.debug("Skipping non-object suggestedDiffNote at finding {}, index {}", findingIndex, noteIndex);
            return null;
        }

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

        // Reject internal workspace paths: the agent sometimes hallucinates inputs/context/ or work/analysis/ paths.
        if (
            filePath.startsWith(SandboxLayout.CONTEXT_PREFIX) ||
            filePath.startsWith(SandboxLayout.HISTORY_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRACTICES_PREFIX) ||
            filePath.startsWith(SandboxLayout.ANALYSIS_PREFIX) ||
            filePath.startsWith(OUTPUT_RELATIVE_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRECOMPUTE_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRECOMPUTE_OUT_PREFIX)
        ) {
            log.debug(
                "Skipping suggestedDiffNote with internal path at finding {}, index {}: {}",
                findingIndex,
                noteIndex,
                filePath
            );
            return null;
        }

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

        Integer endLine = null;
        JsonNode endLineNode = entry.get("endLine");
        if (endLineNode != null && !endLineNode.isNull() && endLineNode.isNumber()) {
            int endLineValue = endLineNode.asInt();
            if (endLineValue >= startLine) {
                endLine = endLineValue;
            }
        }

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

        String title = textField(entry, "title");
        if (title.isBlank()) {
            throw new EntryValidationException("title is blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH - 3) + "...";
        }

        Presence presence = parseEnum(entry, "presence", Presence.class);

        // See parseAssessment: required only when presence carries valence.
        Assessment assessment = parseAssessment(entry, presence);

        // See parseSeverityOrDefault: defaults to INFO rather than discarding the finding.
        Severity severity = parseSeverityOrDefault(entry);

        float confidence = parseConfidence(entry);

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

        List<DiffNote> suggestedDiffNotes = parseSuggestedDiffNotes(entry, index);

        return new ValidatedFinding(
            practiceSlug,
            title,
            presence,
            assessment,
            severity,
            confidence,
            evidence,
            reasoning,
            guidance,
            suggestedDiffNotes
        );
    }

    /**
     * Null exactly when {@code presence} does not {@link Presence#carriesValence() carry valence} — an
     * assessment supplied there is dropped rather than honoured, so a model that couldn't settle an
     * INCONCLUSIVE finding can't smuggle an unearned GOOD into the series. Otherwise required; a missing or
     * unrecognised value discards the entry.
     */
    private static Assessment parseAssessment(JsonNode entry, Presence presence) {
        if (!presence.carriesValence()) {
            return null;
        }
        return parseEnum(entry, "assessment", Assessment.class);
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
     * A missing, null, or non-text value defaults to {@link Severity#INFO} rather than discarding the
     * finding: {@link ValidatedFinding#coerceCoherence(boolean, boolean)} re-derives the real band anyway. A
     * present but unrecognised value still fails the entry.
     */
    private static Severity parseSeverityOrDefault(JsonNode entry) {
        JsonNode node = entry.get("severity");
        if (node == null || node.isNull() || !node.isString()) {
            return Severity.INFO;
        }
        return parseEnum(entry, "severity", Severity.class);
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
        // Detect a percentage (e.g. 85 -> 0.85): the model sometimes emits confidence out of 100.
        if (confidence > 1.0f && confidence <= 100.0f) {
            confidence = confidence / 100.0f;
        }
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new EntryValidationException("confidence out of range [0.0, 1.0]: " + confidence);
        }
        return confidence;
    }

    /**
     * Doubles any backslash that precedes a character invalid after {@code \} in JSON (only
     * {@code " \ / b f n r t u} are valid), turning e.g. Swift's {@code \(var)} interpolation into a literal
     * backslash Jackson can read rather than a malformed escape.
     */
    static String sanitizeJsonEscapes(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 64);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (isValidJsonEscapeChar(next)) {
                    sb.append(c);
                    sb.append(next);
                    i++;
                } else {
                    sb.append('\\');
                    // Don't skip `next`: it is not itself a backslash, so it still needs its own pass.
                    sb.append('\\');
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
     * The orchestrator protocol emits phase markers (e.g. {@code [PHASE0]...}) before its JSON object; this
     * finds the first {@code '{'} that starts a valid object containing a "findings" array.
     */
    @Nullable
    private JsonNode extractJsonFromText(String text) {
        if (text.length() > MAX_RAW_OUTPUT_LENGTH) {
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
                // try the next '{'
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

    public record ParseResult(List<ValidatedFinding> validFindings, List<DiscardedEntry> discarded) {
        static ParseResult empty(String reason) {
            return new ParseResult(List.of(), List.of(new DiscardedEntry(-1, reason)));
        }
    }

    /**
     * @param keys the identities {@code PracticeDetectionDeliveryService.deliver} persisted for this finding,
     *     stamped by the handler rather than recomputed downstream so they cannot drift from the stored
     *     observation. {@code null} until stamped — the parser leaves it unset.
     */
    public record ValidatedFinding(
        String practiceSlug,
        String title,
        Presence presence,
        @Nullable Assessment assessment,
        @Nullable Severity severity,
        float confidence,
        JsonNode evidence,
        String reasoning,
        String guidance,
        List<DiffNote> suggestedDiffNotes,
        @Nullable ObservationKeys keys
    ) {
        /** The parser's output shape: a finding not yet stamped with its persisted identities. */
        public ValidatedFinding(
            String practiceSlug,
            String title,
            Presence presence,
            @Nullable Assessment assessment,
            @Nullable Severity severity,
            float confidence,
            JsonNode evidence,
            String reasoning,
            String guidance,
            List<DiffNote> suggestedDiffNotes
        ) {
            this(
                practiceSlug,
                title,
                presence,
                assessment,
                severity,
                confidence,
                evidence,
                reasoning,
                guidance,
                suggestedDiffNotes,
                null
            );
        }

        public ValidatedFinding withKeys(@Nullable ObservationKeys keys) {
            return new ValidatedFinding(
                practiceSlug,
                title,
                presence,
                assessment,
                severity,
                confidence,
                evidence,
                reasoning,
                guidance,
                suggestedDiffNotes,
                keys
            );
        }

        public @Nullable String recurrenceKey() {
            return keys == null ? null : keys.recurrenceKey();
        }

        public @Nullable String occurrenceKey() {
            return keys == null ? null : keys.occurrenceKey();
        }

        /**
         * Coerces {@code (presence, assessment, severity)} to the system's coherence invariants, independent
         * of what the model emitted:
         * <ol>
         *   <li>A defect-detector practice only ever flags a defect or abstains; a model-emitted
         *       {@code PRESENT, GOOD} there is off-contract noise, coerced to {@code NOT_APPLICABLE}.</li>
         *   <li>Severity is a coaching band only for a {@code BAD} finding (forced null otherwise), and a
         *       {@code BAD} arriving as {@code INFO} is raised to {@code MINOR}.</li>
         *   <li>When {@code advisoryOnly} (not in {@link #BLOCKING_ELIGIBLE_PRACTICES}), a {@code BAD}
         *       finding's {@code CRITICAL}/{@code MAJOR} band is capped to {@code MINOR} so it lands as a
         *       suggestion rather than a merge-blocker.</li>
         * </ol>
         * Idempotent: a no-op coercion returns {@code this}.
         */
        public ValidatedFinding coerceCoherence(boolean isDefectDetector, boolean advisoryOnly) {
            Presence p = presence;
            Assessment a = assessment;
            String r = reasoning;
            if (isDefectDetector && a == Assessment.GOOD) {
                p = Presence.NOT_APPLICABLE;
                a = null;
                r = "[auto-downgraded: defect-detector practice has no clean-bill-of-health observation] " + reasoning;
            }
            // (ABSENT, GOOD) is a legitimate strength per ADR 0022 §1 and is preserved, not collapsed here.
            if (!p.carriesValence()) {
                a = null;
            }
            Severity s =
                a == Assessment.BAD
                    ? (severity == null || severity == Severity.INFO ? Severity.MINOR : severity)
                    : null;
            if (advisoryOnly && a == Assessment.BAD && (s == Severity.CRITICAL || s == Severity.MAJOR)) {
                s = Severity.MINOR;
            }
            if ("avoids-insecure-defaults-and-over-broad-permissions".equals(practiceSlug) && s == Severity.CRITICAL) {
                s = Severity.MAJOR;
            }
            if (p == presence && a == assessment && s == severity) {
                return this;
            }
            return new ValidatedFinding(
                practiceSlug,
                title,
                p,
                a,
                s,
                confidence,
                evidence,
                r,
                guidance,
                suggestedDiffNotes,
                keys
            );
        }
    }

    /**
     * Applies {@link ValidatedFinding#coerceCoherence(boolean, boolean)} to every finding. Returns a fresh
     * mutable list (call sites mutate it downstream for fingerprint stamping). Shared by the PR and Issue
     * handlers so the rule cannot drift between them.
     */
    public static List<ValidatedFinding> coerceCoherence(
        List<ValidatedFinding> findings,
        Set<String> defectDetectorSlugs
    ) {
        List<ValidatedFinding> out = new ArrayList<>(findings.size());
        for (ValidatedFinding f : findings) {
            boolean advisoryOnly = !BLOCKING_ELIGIBLE_PRACTICES.contains(f.practiceSlug());
            out.add(f.coerceCoherence(defectDetectorSlugs.contains(f.practiceSlug()), advisoryOnly));
        }
        return out;
    }

    public record DiscardedEntry(int index, String reason) {}

    /**
     * Pre-rendered delivery content from the agent, alongside the structured findings — the server sanitizes
     * and posts it without further rendering.
     *
     * @param withheld the findings the composer chose not to render, for the ledger to record as SUPPRESSED
     */
    public record DeliveryContent(@Nullable String mrNote, List<DiffNote> diffNotes, List<WithheldFinding> withheld) {
        public DeliveryContent withDiffNotes(List<DiffNote> notes) {
            return new DeliveryContent(mrNote, notes, withheld);
        }
    }

    /**
     * A finding the {@link DeliveryComposer} withheld from the rendered delivery, identified by the
     * {@code occurrenceKey} of the observation it was persisted as — a per-observation identity, so a
     * withheld finding is never confused with another at the same locus.
     */
    public record WithheldFinding(String occurrenceKey, FeedbackSuppressionReason reason) {}

    /**
     * An inline diff note targeting a specific file and line range.
     *
     * @param filePath path relative to repo root (new path, not old)
     * @param endLine  optional last line number for multi-line (GitHub only; GitLab ignores)
     * @param recurrenceKey the stable cross-run identity inherited from the finding this note belongs to, so a
     *     posted placement can be matched back across re-runs; {@code null} until {@link DeliveryComposer}
     *     carries it over from the stamped finding.
     */
    public record DiffNote(
        String filePath,
        int startLine,
        @Nullable Integer endLine,
        String body,
        @Nullable String recurrenceKey
    ) {
        /** The parser's pre-correlation output shape: a note with no correlation key yet. */
        public DiffNote(String filePath, int startLine, @Nullable Integer endLine, String body) {
            this(filePath, startLine, endLine, body, null);
        }
    }
}
