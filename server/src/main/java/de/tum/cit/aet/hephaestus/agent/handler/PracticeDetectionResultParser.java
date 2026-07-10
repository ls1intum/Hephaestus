package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
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
 * Parses structured agent output into validated practice observations. The MR summary is composed
 * server-side by {@link DeliveryComposer}; the agent only supplies observations and per-observation
 * inline diff suggestions.
 *
 * <p>This is a pure function with no Spring dependencies. It never throws — all
 * parse failures are captured in {@link ParseResult#discarded()}.
 *
 * <p>Expected input shape (stored as escaped JSON string at {@code jobOutput.rawOutput}):
 * <pre>{@code
 * {
 *   "observations": [
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

    /** Upper bound on the raw agent output we will materialize/sanitize/parse in memory. */
    private static final int MAX_RAW_OUTPUT_LENGTH = 1_000_000;

    /**
     * Workspace-relative prefix of the collected-output dir, derived from the ABI's absolute {@code OUTPUT_PATH}
     * so the firewall below tracks a rename of the output dir instead of hardcoding {@code "out/"}.
     * {@code OUTPUT_PATH} = {@code WORKSPACE_ROOT + "/out"}, so strip the root and the leading slash, then append one.
     */
    private static final String OUTPUT_RELATIVE_PREFIX =
        SandboxLayout.OUTPUT_PATH.substring(SandboxLayout.WORKSPACE_ROOT.length() + 1) + "/";

    /** Maximum length for the pre-rendered MR/PR summary note (matches PullRequestCommentPoster.MAX_BODY_LENGTH). */
    static final int MAX_MR_NOTE_LENGTH = 60_000;

    /** Maximum length for a single diff note body. */
    static final int MAX_DIFF_NOTE_BODY_LENGTH = 2_000;

    /**
     * The practices whose {@code BAD} observation may legitimately present as a merge-blocker
     * ({@code CRITICAL}/{@code MAJOR}, "fix before merging") — i.e. a problem here can break CORRECTNESS,
     * SECURITY, or DATA INTEGRITY. Every other (craft / process / authoring) practice is ADVISORY: the
     * advisory ceiling in {@link ValidatedObservation#coerceCoherence(boolean, boolean)} caps its band to
     * {@code MINOR} so it lands as a suggestion, never a merge-block.
     *
     * <p>This is a consequence-class delivery policy, general across project kinds (no language/project
     * coupling), co-located with the other delivery-shaping rules. A confidence gate cannot separate
     * advisory from blocking gaps — a craft critique and a real defect are emitted at the same high
     * confidence — so the consequence class, not confidence, is the discriminator that keeps the
     * "fix before merging" signal meaningful. Pinned by {@code PracticeDetectionResultParserTest}.
     */
    static final Set<String> BLOCKING_ELIGIBLE_PRACTICES = Set.of(
        // Correctness: a swallowed error, an unguarded boundary, or a chosen crash on uncontrolled input
        // is a real defect a reviewer should be able to block on.
        "handles-errors-instead-of-swallowing-them",
        "validates-inputs-and-edge-cases-at-the-boundary",
        "avoids-unsafe-panics-and-chosen-crashes",
        // Security / data integrity.
        "validates-and-escapes-untrusted-input",
        "avoids-insecure-defaults-and-over-broad-permissions",
        // A hard-coded credential / secret is a security defect a reviewer must be able to block on; the
        // synthetic secret observation is injected at CRITICAL/MAJOR and must keep that band through coercion.
        "hardcoded-secrets",
        // A dishonest test (always-green, asserting nothing, disabled) actively HIDES correctness defects —
        // worse than a missing test, because it manufactures false safety — so it keeps blocking weight.
        "keeps-the-test-suite-honest"
    );

    /** Maximum number of inline delivery notes per job. This bounds comment API fan-out, not observation detection. */
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
     * Parse agent output into validated observations and optional delivery content. Never throws.
     *
     * @param jobOutput the {@code AgentJob.output} JSONB node (contains {@code rawOutput} string)
     * @return parse result with valid observations, discarded entries, and optional delivery content
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
        JsonNode observationsNode = extractObservationsNode(root);
        if (observationsNode == null || !observationsNode.isArray()) {
            return ParseResult.empty("missing or non-array 'observations' field");
        }
        if (observationsNode.isEmpty()) {
            return ParseResult.empty("observations array is empty");
        }

        List<ValidatedObservation> valid = new ArrayList<>();
        List<DiscardedEntry> discarded = new ArrayList<>();
        for (int i = 0; i < observationsNode.size(); i++) {
            JsonNode entry = observationsNode.get(i);
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

    private JsonNode extractObservationsNode(JsonNode root) {
        return root.get("observations");
    }

    /** Parse the {@code suggestedDiffNotes} array on a single observation (may be absent). */
    private List<DiffNote> parseSuggestedDiffNotes(JsonNode entry, int observationIndex) {
        JsonNode suggestedNode = entry.get("suggestedDiffNotes");
        if (suggestedNode == null || suggestedNode.isNull() || !suggestedNode.isArray()) {
            return List.of();
        }
        List<DiffNote> notes = new ArrayList<>();
        for (int j = 0; j < suggestedNode.size(); j++) {
            DiffNote note = parseSingleDiffNote(suggestedNode.get(j), observationIndex, j);
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
    private DiffNote parseSingleDiffNote(JsonNode entry, int observationIndex, int noteIndex) {
        if (!entry.isObject()) {
            log.debug("Skipping non-object suggestedDiffNote at observation {}, index {}", observationIndex, noteIndex);
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
            log.debug(
                "Skipping suggestedDiffNote at observation {}, index {}: missing filePath",
                observationIndex,
                noteIndex
            );
            return null;
        }
        String filePath = filePathNode.asString();

        // Reject internal workspace paths — agent sometimes hallucinates inputs/context/ or work/analysis/ paths
        if (
            filePath.startsWith(SandboxLayout.CONTEXT_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRACTICES_PREFIX) ||
            filePath.startsWith(SandboxLayout.ANALYSIS_PREFIX) ||
            filePath.startsWith(OUTPUT_RELATIVE_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRECOMPUTE_PREFIX) ||
            filePath.startsWith(SandboxLayout.PRECOMPUTE_OUT_PREFIX)
        ) {
            log.debug(
                "Skipping suggestedDiffNote with internal path at observation {}, index {}: {}",
                observationIndex,
                noteIndex,
                filePath
            );
            return null;
        }

        // Required: startLine (positive integer)
        JsonNode startLineNode = entry.get("startLine");
        if (startLineNode == null || startLineNode.isNull() || !startLineNode.isNumber()) {
            log.debug(
                "Skipping suggestedDiffNote at observation {}, index {}: missing or non-numeric startLine",
                observationIndex,
                noteIndex
            );
            return null;
        }
        int startLine = startLineNode.asInt();
        if (startLine <= 0) {
            log.debug(
                "Skipping suggestedDiffNote at observation {}, index {}: startLine must be positive, got {}",
                observationIndex,
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
            log.debug(
                "Skipping suggestedDiffNote at observation {}, index {}: missing body",
                observationIndex,
                noteIndex
            );
            return null;
        }
        String body = bodyNode.asString();
        if (body.length() > MAX_DIFF_NOTE_BODY_LENGTH) {
            log.debug(
                "Truncating suggestedDiffNote body from {} to {} chars at observation {}, index {}",
                body.length(),
                MAX_DIFF_NOTE_BODY_LENGTH,
                observationIndex,
                noteIndex
            );
            body = body.substring(0, MAX_DIFF_NOTE_BODY_LENGTH);
        }

        return new DiffNote(filePath, startLine, endLine, body);
    }

    // Observation entry validation

    private ValidatedObservation validateEntry(JsonNode entry, int index) {
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

        // Required: presence
        Presence presence = parseEnum(entry, "presence", Presence.class);

        // Required (unless NOT_APPLICABLE): assessment. The detector decides GOOD/BAD per observation by
        // reading the criteria + what_good_looks_like. NOT_APPLICABLE has no valence (forced null); any other
        // presence with a missing/blank assessment is genuinely malformed and the entry is discarded.
        Assessment assessment = parseAssessment(entry, presence);

        // Optional: severity. Severity is a coaching band only for a BAD observation (coerceCoherence forces null
        // otherwise), and the model routinely omits it elsewhere. A missing/null severity defaults to INFO
        // rather than discarding an otherwise-valid observation; coerceCoherence then re-derives the final band
        // (e.g. a BAD with no severity floors to MINOR, and a non-BAD observation's severity is nulled out).
        Severity severity = parseSeverityOrDefault(entry);

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

        // Optional: per-observation suggested diff notes — the agent's inline-comment suggestions.
        List<DiffNote> suggestedDiffNotes = parseSuggestedDiffNotes(entry, index);

        // Optional: subjectLogin — the reviewer login the model proposes a reviewer-audience observation is about.
        // The MODEL only proposes; the SERVER validates it against the real reviewer set before it can drive
        // attribution (see PracticeDetectionDeliveryService). Passed through trimmed, or null when absent.
        String subjectLogin = optionalTextField(entry, "subjectLogin");
        if (subjectLogin != null) {
            subjectLogin = subjectLogin.trim();
            if (subjectLogin.isEmpty()) {
                subjectLogin = null;
            }
        }

        return new ValidatedObservation(
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
            subjectLogin,
            null
        );
    }

    /**
     * Parses the {@code assessment} valence. NULL iff presence is {@link Presence#NOT_APPLICABLE} (an
     * inapplicable practice has no valence — any assessment supplied there is ignored). For any other
     * presence the detector must supply a recognised {@code GOOD}/{@code BAD}; a missing or unrecognised
     * value discards the entry (genuinely malformed output worth surfacing).
     */
    private static Assessment parseAssessment(JsonNode entry, Presence presence) {
        if (presence == Presence.NOT_APPLICABLE) {
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
     * Parses the optional {@code severity}. A missing, null, or non-text value defaults to
     * {@link Severity#INFO} — the model commonly omits severity on non-BAD observations, and
     * {@link ValidatedObservation#coerceCoherence(boolean, boolean)} re-derives the final band regardless (nulling it
     * out unless {@code assessment == BAD}), so discarding such an observation would silently drop valid
     * coaching. A present but unrecognised value still fails the entry (genuinely malformed output).
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
     * Extract a JSON object containing "observations" from mixed text content.
     *
     * <p>The orchestrator protocol emits phase markers (e.g., {@code [PHASE0]...}) followed
     * by a JSON object. This method finds the first '{' that starts a valid JSON object
     * containing a "observations" array.
     */
    @Nullable
    private JsonNode extractJsonFromText(String text) {
        // Guard: skip absurdly large inputs (agent rawOutput shouldn't exceed MAX_RAW_OUTPUT_LENGTH)
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
                if (node != null && node.isObject() && node.has("observations")) {
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
     * @param validObservations validated observations from the agent output
     * @param discarded entries that failed validation with reasons
     */
    public record ParseResult(List<ValidatedObservation> validObservations, List<DiscardedEntry> discarded) {
        static ParseResult empty(String reason) {
            return new ParseResult(List.of(), List.of(new DiscardedEntry(-1, reason)));
        }
    }

    /**
     * @param subjectLogin the reviewer login the MODEL proposes a reviewer-audience observation is about (the
     *     server VALIDATES it against the real reviewer set — see {@code ReviewerResolver} — and never trusts
     *     it for attribution beyond a login lookup). {@code null} for author-audience observations and whenever
     *     the model omitted it. The parser passes it through untouched (trimmed); attribution happens later in
     *     {@code PracticeDetectionDeliveryService}.
     * @param recurrenceKey the stable cross-run {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint}
     *     identity, stamped by {@code PullRequestReviewHandler} from the value
     *     {@code PracticeDetectionDeliveryService.deliver} already computed (never recomputed downstream, so it
     *     cannot drift from the persisted observation). {@code null} until stamped — the parser leaves it unset.
     */
    public record ValidatedObservation(
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
        @Nullable String subjectLogin,
        @Nullable String recurrenceKey
    ) {
        /** Pre-correlation compatibility shape: an observation with no subject login and no correlation key yet. */
        public ValidatedObservation(
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
                null,
                null
            );
        }

        /**
         * Compatibility shape for call sites that stamp only the correlation key (subject login absent — the
         * author-audience default). Keeps the existing {@code (…suggestedDiffNotes, recurrenceKey)} arity valid.
         */
        public ValidatedObservation(
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
            @Nullable String recurrenceKey
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
                null,
                recurrenceKey
            );
        }

        /** Returns a copy carrying {@code login} as the model-proposed subject; other components preserved. */
        public ValidatedObservation withSubjectLogin(@Nullable String login) {
            return new ValidatedObservation(
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
                login,
                recurrenceKey
            );
        }

        /** Returns a copy stamped with {@code key}; all other components are preserved by reference. */
        public ValidatedObservation withRecurrenceKey(@Nullable String key) {
            return new ValidatedObservation(
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
                subjectLogin,
                key
            );
        }

        /**
         * Returns a copy with {@code (presence, assessment, severity)} coerced to the system's coherence
         * invariants, independent of what the (weak) model emitted:
         * <ol>
         *   <li><b>Defect-detector has no clean bill of health.</b> A practice declaring {@code DEFECT-DETECTOR
         *       DISCIPLINE} either flags a defect ({@code PRESENT, BAD}) or abstains ({@code NOT_APPLICABLE}); a
         *       model-emitted {@code PRESENT, GOOD} there is a clean bill of health that would ship as a false
         *       strength — coerce it to {@code NOT_APPLICABLE} (assessment null).</li>
         *   <li><b>Severity sentinel.</b> Severity is a coaching band only for a {@code BAD} observation; it is
         *       forced null otherwise, and a {@code BAD} that arrived as {@code INFO} (a defect with no band)
         *       is raised to {@code MINOR}.</li>
         *   <li><b>Advisory ceiling.</b> When {@code advisoryOnly} (the practice is craft/process/authoring, not
         *       in {@link #BLOCKING_ELIGIBLE_PRACTICES}), a {@code BAD} observation may never present as a
         *       merge-blocker, so its {@code CRITICAL}/{@code MAJOR} band is capped to {@code MINOR}. This lands
         *       the lesson as a suggestion rather than a "fix before merging" — reserving the blocking signal for
         *       correctness/security/data-integrity practices so the rare real blocker is not drowned out by the
         *       many high-confidence craft critiques. The coerced band is carried by the returned observation and is
         *       what persists (and delivers).</li>
         * </ol>
         * Idempotent: a no-op coercion returns {@code this}. The list helper
         * {@link PracticeDetectionResultParser#coerceCoherence(List, Set)} is the sole production entry point.
         */
        public ValidatedObservation coerceCoherence(boolean isDefectDetector, boolean advisoryOnly) {
            Presence p = presence;
            Assessment a = assessment;
            String r = reasoning;
            if (isDefectDetector && a == Assessment.GOOD) {
                // A defect-detector practice only ever emits a problem (PRESENT, BAD) or NOT_APPLICABLE; it has
                // no clean-bill-of-health strength. Any GOOD it emits — at either presence — is off-contract
                // model noise, so downgrade to NOT_APPLICABLE rather than ship a false strength to the student.
                p = Presence.NOT_APPLICABLE;
                a = null;
                r = "[auto-downgraded: defect-detector practice has no clean-bill-of-health observation] " + reasoning;
            }
            // For a normal practice, (ABSENT, GOOD) is a legitimate strength per ADR 0022 §1 — "bad behaviour
            // avoided → clean" — and is preserved, NOT collapsed to NOT_APPLICABLE.
            // assessment must be null exactly when presence is NOT_APPLICABLE.
            if (p == Presence.NOT_APPLICABLE) {
                a = null;
            }
            Severity s =
                a == Assessment.BAD
                    ? (severity == null || severity == Severity.INFO ? Severity.MINOR : severity)
                    : null;
            if (advisoryOnly && a == Assessment.BAD && (s == Severity.CRITICAL || s == Severity.MAJOR)) {
                s = Severity.MINOR;
            }
            if (p == presence && a == assessment && s == severity) {
                return this;
            }
            return new ValidatedObservation(
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
                subjectLogin,
                recurrenceKey
            );
        }
    }

    /**
     * Apply {@link ValidatedObservation#coerceCoherence(boolean, boolean)} to every observation, passing the per-observation
     * defect-detector flag from {@code defectDetectorSlugs}. Returns a fresh mutable list (call sites mutate
     * it downstream for fingerprint stamping). Shared by the PR and Issue handlers so the rule lives in one
     * place and cannot drift between them.
     */
    public static List<ValidatedObservation> coerceCoherence(
        List<ValidatedObservation> observations,
        Set<String> defectDetectorSlugs
    ) {
        List<ValidatedObservation> out = new ArrayList<>(observations.size());
        for (ValidatedObservation f : observations) {
            boolean advisoryOnly = !BLOCKING_ELIGIBLE_PRACTICES.contains(f.practiceSlug());
            out.add(f.coerceCoherence(defectDetectorSlugs.contains(f.practiceSlug()), advisoryOnly));
        }
        return out;
    }

    public record DiscardedEntry(int index, String reason) {}

    /**
     * Pre-rendered delivery content from the agent. The agent produces this alongside
     * structured observations — the server sanitizes and posts it without further rendering.
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
     * @param recurrenceKey the stable cross-run identity inherited from the observation this note belongs to, so a
     *     posted placement can be matched back across re-runs; {@code null} until {@link DeliveryComposer}
     *     carries it over from the stamped observation (the parser leaves it unset).
     */
    public record DiffNote(
        String filePath,
        int startLine,
        @Nullable Integer endLine,
        String body,
        @Nullable String recurrenceKey
    ) {
        /** Pre-correlation compatibility shape: a note with no correlation key yet (the parser's output). */
        public DiffNote(String filePath, int startLine, @Nullable Integer endLine, String body) {
            this(filePath, startLine, endLine, body, null);
        }

        /** Returns a copy stamped with {@code key}; all other components are preserved. */
        public DiffNote withRecurrenceKey(@Nullable String key) {
            return new DiffNote(filePath, startLine, endLine, body, key);
        }
    }
}
