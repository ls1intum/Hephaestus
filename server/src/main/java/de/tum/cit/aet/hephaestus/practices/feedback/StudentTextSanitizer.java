package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Shared scrub for any text headed to a student, on ANY surface (the SCM delivery composer, the reflective
 * dashboard, the mentor context files). The detection model echoes its own grading vocabulary — rubric bands,
 * presence/assessment tuples, criteria flowcharts, bucket arithmetic, cross-practice orchestration narration —
 * into the {@code reasoning}/{@code guidance} it writes. None of that is feedback; it is the grader talking to
 * itself. This util drops the whole sentence wherever that vocabulary appears, then repairs the JSON-envelope
 * corruption the model occasionally leaks into a guidance value.
 *
 * <p>Extracted from {@code DeliveryComposer} so the read-model surfaces (dashboard / mentor) inherit the SAME
 * firewall the PR comment already has — a finding cannot reach a learner rubric-voiced on any path out
 * (SYSTEMIC firewall, audit gap #1). Idempotent and locale-safe (no naked case folding).
 */
public final class StudentTextSanitizer {

    private StudentTextSanitizer() {}

    /**
     * Catches grading-meta phrasings the detection model can emit: "the practice requires…", "for a OBSERVED
     * observation", "MINOR severity level/band", "acceptable upper band", "according to/violating the
     * practice", "…line threshold", presence/assessment rubric tuples, criteria flowchart vocabulary, and the
     * cross-practice orchestration narration the grader writes to itself.
     */
    private static final Pattern GRADING_SENTENCE = Pattern.compile(
        "(?i)(" +
            "\\bthe\\s+practice\\s+(?:requires|defines|expects|mandates|deems|treats|considers|states|flags)\\b|" +
            "\\b(?:according to|per|under|following|violat\\w+|satisf\\w+|fail\\w*)\\s+the\\s+practice\\b|" +
            "\\b(?:OBSERVED|NOT[_ ]OBSERVED|NOT[_ ]APPLICABLE)\\s+(?:observation|finding|result|rating)\\b|" +
            "\\b(?:for|to|a|an|the)\\s+(?:OBSERVED|NOT[_ ]OBSERVED|NOT[_ ]APPLICABLE)\\s+(?:observation|finding)\\b|" +
            "\\b(?:MINOR|MAJOR|INFO|CRITICAL)\\s+(?:severity|band|bucket|tier)\\b|" +
            "\\bseverity\\s+(?:level|band|bucket|rating)\\b|" +
            "\\b(?:upper|lower|acceptable)\\s+band\\b|" +
            "\\b[≤<=>]*\\s*\\d+[\\s-]*(?:line|file)s?\\s+threshold\\b|" +
            "\\bthreshold\\s+for\\s+a\\s+\\w+\\s+(?:observation|finding)\\b|" +
            // Rubric-mechanics / criteria-computation phrasings the model can echo (it repeats the
            // criteria's internal bucket maths and preamble tags into the reasoning). Drop the whole sentence.
            "\\braw\\s+bucket\\b|" +
            "->\\s*(?:MAJOR|MINOR|INFO|CRITICAL|OBSERVED|NOT[_ ]OBSERVED|NOT[_ ]APPLICABLE|PRESENT|ABSENT|GOOD|BAD)\\b|" +
            // ADR-0022 presence/assessment rubric vocabulary the model is now prompted on. "the presence is
            // ABSENT", "the assessment is BAD", or the tuple "(PRESENT, GOOD)" are the grader narrating the
            // rubric to itself — drop the whole sentence.
            "\\b(?:presence|assessment)\\s+is\\s+(?:PRESENT|ABSENT|NOT[_ ]APPLICABLE|GOOD|BAD)\\b|" +
            "\\((?:PRESENT|ABSENT|NOT[_ ]APPLICABLE)\\s*,\\s*(?:GOOD|BAD)\\)|" +
            "\\b(?:DEFECT-DETECTOR|OBSERVED\\s+DISCIPLINE|GROUNDING\\s+GATE|EPIC\\s+EXCEPTION|EPIC/CORE-REQUIREMENT)\\b|" +
            "\\benriched\\s*[=:]|" +
            "\\b[AUDFN]\\s*\\+\\s*[AUDFN]\\s*==?\\s*\\d|" + // grader bucket arithmetic e.g. A+D=4420 (two-operand)
            "\\b[ADF]\\s*=\\s*\\d{2,}|" + // single multi-digit metric e.g. A=4094, F=28, D=326 (not prose "N = 3")
            "\\([A-Z]\\s*=\\s*\\d+\\)|" + // a parenthesised counter, e.g. "(T = 13)", "(K = 3)" — criteria scoring vars
            "\\bgiving\\s+[A-Z]\\s*=\\s*\\d+\\b|" + // "…combine distinct concerns with 'and', giving K = 3"
            "\\b(?:additions?|deletions?|changed[_ ]files?)\\s*[=:]\\s*\\d|" +
            "\\bgenerated/vendored\\s+(?:check|exclusion|dominance)\\b|" +
            "\\bpartition\\s+after\\b|" +
            "\\bnoiseFraction\\b|" +
            // Cross-practice ORCHESTRATION leaks: the model narrates how findings were routed between
            // practices ("sole owner (cross-practice)", "ready-and-traceable-handoff suppressed its …",
            // "ships-tests-with-the-change emitted NOT_APPLICABLE, both deferring here", "team-wide standing
            // nudge, never a per-MR blocker"). This is the grader talking to itself about ownership/delivery,
            // never feedback to the student — drop the whole sentence.
            "\\bcross-practice\\b|" +
            "\\bsole\\s+owner\\b|" +
            "\\bdeferr(?:ing|ed|s)\\b|" +
            "\\bemit(?:ted|s|ting)?\\s+NOT[_ ]APPLICABLE\\b|" +
            "\\bsuppress(?:ed|es|ing)\\s+its\\b|" +
            "\\b(?:team-wide\\s+)?standing\\s+nudge\\b|" +
            "\\bper-MR\\s+blocker\\b|" +
            // The model echoes the criteria's classifier flowchart into student-facing reasoning — band maths,
            // gate predicates, catalogue names, and pipeline plumbing. Each lesson survives in the title +
            // guidance without any of this, so drop the sentence.
            "→\\s*(?:MAJOR|MINOR|INFO|CRITICAL|OBSERVED|NOT[_ ]OBSERVED|NOT[_ ]APPLICABLE|PRESENT|ABSENT|GOOD|BAD)\\b|" + // unicode-arrow band routing
            "\\bPer\\s+the\\s+(?:fixed\\s+)?(?:bucketing|criteria|severity\\s+rules?)\\b|" +
            "\\bunder\\s+the\\s+criteria\\b|" +
            "\\b(?:largeness|coherence|spread|epic|significance)\\s+gate\\b|" +
            "\\bsignal\\s+i{1,3}\\b|" + // "signal ii — >=3 distinct parts"
            "\\bsignificance\\s+catalogue\\b|\\bcatalogue\\s+entry\\b|" +
            "\\bsub-check\\b|" +
            "\\bnon-epic\\s+body\\b|" +
            "\\bcombined\\s+severity\\b|\\bmost\\s+severe\\s+sub-result\\b|" +
            "\\bcarve-out\\b|" +
            "\\bthreshold\\s+for\\s+downgrade\\b|\\b\\d+%\\s+threshold\\b|" +
            "\\bis\\s+(?:MINOR|MAJOR|INFO|CRITICAL)\\s*(?:\\([^)]*\\)\\s*)?,?\\s+not\\s+(?:MINOR|MAJOR|INFO|CRITICAL)\\b|" + // "is MINOR, not MAJOR" — tolerate an intervening "(a decomposition nudge)," parenthetical
            // Observation-justification phrasings the model can emit verbatim: the grader narrating WHY a
            // observation/severity landed. Each lesson stands on the title + guidance + the severity icon without
            // this machinery — drop the whole sentence.
            "\\bobservation\\s+is\\s+(?:OBSERVED|NOT[_ ]OBSERVED|NOT[_ ]APPLICABLE)\\b|" + // "the combined observation is NOT_OBSERVED" (enum AFTER the noun)
            "\\bcapped\\s+at\\s+(?:MINOR|MAJOR|INFO|CRITICAL)\\b|" + // severity-cap arithmetic: "even a fully absent rationale would be capped at MINOR"
            "\\bumbrella\\s+calibration\\b|" + // "Per the umbrella calibration … is MINOR"
            "\\breason\\s+connective\\b|" + // "no sentence uses a reason connective such as 'so that', 'because' …"
            "\\brollup\\b|" +
            // Pipeline plumbing: internal context/precompute filenames + input-reconciliation narration.
            "\\bdiff_stat\\.txt\\b|\\bdiff_summary\\.md\\b|\\bmetadata\\.(?:body|json)\\b|" +
            "\\bso\\s+the\\s+diff\\s+is\\s+trusted\\b|\\bmaterial\\s+disagreement\\b|" +
            "\\bafter\\s+scanning\\b|" +
            // Raw snake_case API field tokens reaching prose, e.g. "sub_issues_total is null".
            "\\b[a-z]+(?:_[a-z]+)+\\s+(?:is|are)\\s+(?:null|present|set|empty)\\b|" +
            "\\bsub_issues_total\\b|" +
            // Scoring-machinery phrasings the model can emit:
            // "noise fraction (2/14 ≈ 0.14) is ≤ 0.25, so the severity is INFO", "is_draft false, no WIP token",
            // "satisfying the categorizing-label requirement". Each lesson stands on the title + guidance alone.
            "\\bnoise\\s+fraction\\b|" + // space variant of noiseFraction
            "\\bseverity\\s+is\\s+(?:MINOR|MAJOR|INFO|CRITICAL)\\b|" + // "so the severity is INFO"
            "[≤≥<>]=?\\s*0?\\.\\d+|" + // a bare ratio threshold comparison, e.g. "≤ 0.25"
            "\\bis_draft\\b|\\bWIP\\s+token\\b|" + // raw readiness field/token names
            "\\bsatisf\\w+\\s+the\\s+[\\w-]+\\s+requirement\\b" + // "satisfying the categorizing-label requirement"
            ")"
    );

    /**
     * Matches the whitespace run that separates two sentences (a sentence-ending [.!?] then whitespace).
     * Used to tokenise student text while preserving the original separator, so Markdown lists and
     * headings (whose items end in '.') keep their newlines instead of being folded onto one line.
     */
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * A closing brace/bracket immediately followed by ≥1 quote/backslash at the very end of the text:
     * the serialized-object boundary the agent occasionally leaks INTO a guidance value (observed as a
     * {@code '"}"} tail in the model output). A legitimate inline JSON example ends AT the brace
     * ({@code {"k":"v"}}) with nothing after it — the trailing outer quote is the discriminator, so this
     * never fires on a real code/JSON snippet that simply closes with a brace.
     */
    private static final Pattern ENVELOPE_TAIL = Pattern.compile("[\"'\\\\]*[}\\]][\"'\\\\]+\\s*$");

    /** True when the text is pure rubric meta on its own — used to drop a whole bullet/quote wholesale. */
    public static boolean isGradingMeta(@Nullable String text) {
        return text != null && GRADING_SENTENCE.matcher(text).find();
    }

    /**
     * Strips internal grading vocabulary from text headed to a student: split into sentences, drop any
     * that is pure rubric meta ({@link #GRADING_SENTENCE}), then tidy the whitespace the drops leave
     * behind. Idempotent and locale-safe (no naked case folding).
     */
    public static String sanitize(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        // Unescape literal \n / \t the agent sometimes emits in reasoning/guidance (a JSON escape that
        // should render as a real line break, e.g. "e.g.:\n- ..."), so it reads as Markdown not raw text.
        text = text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "    ");
        // Walk sentence-ending punctuation followed by whitespace so a mid-paragraph rubric sentence is
        // removed wholesale. Preserve the ORIGINAL inter-sentence separator (notably newlines that form
        // Markdown lists/headings) for every sentence we keep — rejoining with a blanket space would
        // collapse a bulleted acceptance-criteria block into one run-on line.
        StringBuilder kept = new StringBuilder(text.length());
        Matcher sep = SENTENCE_SEPARATOR.matcher(text);
        int pos = 0;
        while (sep.find()) {
            String sentence = text.substring(pos, sep.start());
            if (!GRADING_SENTENCE.matcher(sentence).find()) {
                kept.append(sentence).append(text, sep.start(), sep.end());
            }
            pos = sep.end();
        }
        String tail = text.substring(pos);
        if (!GRADING_SENTENCE.matcher(tail).find()) {
            kept.append(tail);
        }
        String out = kept.toString();
        // Tidy the gaps the drops leave: doubled spaces, space-before-punctuation, blank lines. Use
        // [ \t] (not \s) so list/heading newlines are never folded away.
        out = out.replaceAll("[ \\t]{2,}", " ").replaceAll("[ \\t]+([.,;])", "$1").replaceAll("\\n{3,}", "\n\n");
        return stripEnvelopeCorruption(out.strip());
    }

    /**
     * Repairs the JSON-envelope corruption that occasionally reaches student text: the model terminates a
     * guidance string with a leaked object boundary ({@code …quality'"}"}), often after echoing the final
     * clause ({@code …quality'"ws to adjust…quality}). Only runs when the unmistakable {@link #ENVELOPE_TAIL}
     * signature is present, so well-formed guidance — even guidance that legitimately repeats a phrase or
     * ends in a brace — is never touched. On match: drop the artifact, undo the duplicated trailing run, and
     * trim the dangling quote/space the cut leaves. Idempotent.
     */
    public static String stripEnvelopeCorruption(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher m = ENVELOPE_TAIL.matcher(text);
        if (!m.find()) {
            return text;
        }
        String head = dropDuplicatedTail(text.substring(0, m.start()), 12);
        // Trim the dangling quote/backslash/space the splice leaves (e.g. a now-unbalanced opening quote).
        return head.replaceAll("[\"'\\\\\\s]+$", "").stripTrailing();
    }

    /**
     * If {@code s} ends with a run of ≥{@code minLen} chars that also occurs earlier, cut back to the end of
     * that earlier occurrence — removing the duplicate and any splice junk between the two copies. Scoped to
     * the corruption path ({@link #stripEnvelopeCorruption}) only, so a legitimately repeated phrase in
     * normal guidance is never trimmed.
     */
    private static String dropDuplicatedTail(String s, int minLen) {
        int n = s.length();
        for (int len = n / 2; len >= minLen; len--) {
            String suffix = s.substring(n - len);
            int earlier = s.lastIndexOf(suffix, n - len - 1);
            if (earlier >= 0) {
                return s.substring(0, earlier + len);
            }
        }
        return s;
    }
}
