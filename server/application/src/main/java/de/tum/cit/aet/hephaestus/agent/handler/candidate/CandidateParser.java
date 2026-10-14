package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CANDIDATE_BYTES;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CITATIONS_PER_PROBLEM;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CONSULTED_SOURCES;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.EXAMPLE_NOTE_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.MAX_COUNT;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.NEXT_STEP_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OMISSIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OMISSION_ITEMS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OPEN_QUESTIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OPEN_QUESTION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEMS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEM_EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.QUOTE_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.RESULT_CITATIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.SUMMARY_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.WARRANT_FIELD_CHARS;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.ReadReceiptStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads one candidate with the rules of {@code parseCandidate} in {@code lib/contract.ts}: first the strict superset
 * of every key any outcome may carry, where an unknown key, a wrong type or an exceeded bound is
 * {@link CandidateRefusal#MALFORMED_CANDIDATE}; then the outcome rules in {@link CandidateRefusal} order, the first
 * failure naming the refusal. Nothing is repaired or guessed: a leads return is malformed, as it is.
 */
public final class CandidateParser {

    private static final int MAX_LINE = Integer.MAX_VALUE;

    /** {@code OMISSION_REASONS} in {@code lib/contract.ts}. */
    public static final List<String> OMISSION_REASONS = List.of(
            "selection",
            "truncated",
            "unavailable",
            "budget",
            "deadline",
            "too-large",
            "off-format",
            "refused",
            "error",
            "cancelled");

    private static final Set<String> SUPERSET_KEYS =
            Set.of("outcome", "summary", "explanation", "citations", "problems", "example", "evidence", "coverage");
    private static final Set<String> CITATION_KEYS =
            Set.of("sourceKind", "sourcePath", "path", "side", "revision", "startLine", "endLine", "quote");
    private static final Set<String> PROBLEM_KEYS =
            Set.of("key", "severity", "summary", "explanation", "citations", "basis", "nextStep");
    private static final Set<String> EVIDENCE_KEYS = Set.of("search", "inapplicability", "openQuestions");
    private static final Map<String, Set<String>> BASIS_KEYS = Map.of(
            "introduced", Set.of("type", "head", "explanation"),
            "worsened", Set.of("type", "base", "head", "explanation"),
            "duty", Set.of("type", "criterionQuote", "event", "explanation"),
            "state", Set.of("type", "explanation"));
    private static final Map<String, Set<String>> COVERAGE_KEYS = Map.of(
            "host", Set.of("source", "reads", "truncatedReads", "failedReads", "receipts", "complete", "omitted"),
            "claimed", Set.of("source", "selected", "checked", "total", "complete", "omitted"));

    private CandidateParser() {}

    /**
     * The candidate this value is, or why it is not one.
     *
     * @throws CandidateRefusedException with the code of the first rule the value breaks
     */
    public static Candidate parse(@Nullable JsonNode value) {
        if (value == null || value.isMissingNode()) {
            throw refuse(CandidateRefusal.MALFORMED_CANDIDATE, "the candidate is missing");
        }
        long bytes = javaScriptJsonBytes(value);
        if (bytes > CANDIDATE_BYTES) {
            throw refuse(
                    CandidateRefusal.MALFORMED_CANDIDATE,
                    "the candidate has " + bytes + " bytes; at most " + CANDIDATE_BYTES);
        }
        Superset sent;
        try {
            sent = superset(value);
        } catch (Malformed malformed) {
            throw new CandidateRefusedException(
                    CandidateRefusal.MALFORMED_CANDIDATE, String.valueOf(malformed.getMessage()), malformed);
        }
        return rules(sent);
    }

    /**
     * {@code Buffer.byteLength(JSON.stringify(value))}, the size {@code parseCandidate} bounds. Jackson writes other
     * bytes for the same tree: {@code 3.0} where JavaScript writes {@code 3}, and an escape where JavaScript writes the
     * UTF-8 of a surrogate pair.
     */
    static long javaScriptJsonBytes(JsonNode value) {
        if (value.isObject()) {
            long bytes = 2L + Math.max(0, value.size() - 1);
            for (Map.Entry<String, JsonNode> property : value.properties()) {
                bytes += javaScriptStringBytes(property.getKey()) + 1 + javaScriptJsonBytes(property.getValue());
            }
            return bytes;
        }
        if (value.isArray()) {
            long bytes = 2L + Math.max(0, value.size() - 1);
            for (JsonNode item : value) {
                bytes += javaScriptJsonBytes(item);
            }
            return bytes;
        }
        if (value.isString()) {
            return javaScriptStringBytes(value.asString());
        }
        if (value.isNumber()) {
            // Only integers can sit in a valid candidate, and JavaScript writes them without a fraction. Any other
            // number is malformed whatever the size, so its approximate length cannot change the refusal.
            double number = value.asDouble();
            return number == Math.rint(number) && Math.abs(number) < 0x1p63
                    ? Long.toString((long) number).length()
                    : value.toString().length();
        }
        return value.toString().length();
    }

    private static long javaScriptStringBytes(String text) {
        long bytes = 2;
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '"' || c == '\\' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
                bytes += 2;
            } else if (c < 0x20) {
                bytes += 6;
            } else if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c)
                    && at + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(at + 1))) {
                bytes += 4;
                at++;
            } else if (Character.isSurrogate(c)) {
                bytes += 6;
            } else {
                bytes += 3;
            }
            at++;
        }
        return bytes;
    }

    private static Candidate rules(Superset sent) {
        ResultOutcome outcome = sent.outcome();
        List<CandidateProblem> problems = sent.problems();
        boolean listsProblems = problems != null && !problems.isEmpty();
        if ((outcome == ResultOutcome.NOT_MET) != listsProblems || (problems != null && problems.isEmpty())) {
            throw refuse(
                    CandidateRefusal.OUTCOME_PROBLEM_MISMATCH,
                    outcome == ResultOutcome.NOT_MET
                            ? "a NOT_MET result lists its problems"
                            : "a " + outcome + " result lists no problem");
        }
        boolean search = sent.search() != null;
        boolean inapplicability = sent.inapplicability() != null;
        boolean undecidability = sent.openQuestions() != null;
        boolean warrants =
                switch (outcome) {
                    case MET -> !inapplicability && !undecidability;
                    case NOT_MET -> !inapplicability;
                    case DOES_NOT_APPLY -> inapplicability && !search && !undecidability;
                    case COULD_NOT_DETERMINE -> !inapplicability && undecidability;
                };
        if (!warrants) {
            throw refuse(
                    CandidateRefusal.WARRANT_OUTCOME_MISMATCH,
                    switch (outcome) {
                        case MET -> "a MET result may carry a search warrant only";
                        case NOT_MET -> "a NOT_MET result may carry a search warrant and open questions only";
                        case DOES_NOT_APPLY -> "a DOES_NOT_APPLY result carries its inapplicability warrant only";
                        case COULD_NOT_DETERMINE ->
                            "an COULD_NOT_DETERMINE result carries its open questions, and a search warrant at most";
                    });
        }
        if (outcome != ResultOutcome.NOT_MET && sent.citations().isEmpty()) {
            throw refuse(CandidateRefusal.RESULT_CITATIONS_REQUIRED, "a " + outcome + " result cites what it rests on");
        }
        boolean complete = sent.coverage().path("complete").asBoolean();
        if (outcome == ResultOutcome.MET && !complete) {
            throw refuse(CandidateRefusal.MET_COVERAGE_INCOMPLETE, "a MET result needs complete coverage");
        }
        String coverageProblem = coverageProblem(sent.coverage());
        if (coverageProblem != null) {
            throw refuse(CandidateRefusal.COVERAGE_INCONSISTENT, coverageProblem);
        }
        ObjectNode example = sent.example();
        if (example != null && outcome != ResultOutcome.MET && outcome != ResultOutcome.NOT_MET) {
            throw refuse(CandidateRefusal.EXAMPLE_OUTCOME_MISMATCH, "a " + outcome + " result has no example");
        }
        if (example != null && problems != null) {
            JsonNode exampleCitation = example.path("citation");
            for (CandidateProblem problem : problems) {
                for (ObjectNode cited : problem.citations()) {
                    if (overlaps(cited, exampleCitation)) {
                        throw refuse(
                                CandidateRefusal.EXAMPLE_OVERLAPS_PROBLEM, "the example cites the place of a problem");
                    }
                }
            }
        }
        if (problems != null) {
            for (int index = 0; index < problems.size(); index++) {
                CandidateProblem problem = problems.get(index);
                for (int at : basisIndices(problem.basis())) {
                    if (at >= problem.citations().size()) {
                        throw refuse(
                                CandidateRefusal.BASIS_CITATION_OUT_OF_RANGE,
                                "problem " + index + ": its basis names citation " + at + ", and it has "
                                        + problem.citations().size());
                    }
                }
            }
            Set<String> keys = new HashSet<>();
            for (CandidateProblem problem : problems) {
                if (problem.key() != null && !keys.add(problem.key())) {
                    throw refuse(CandidateRefusal.DUPLICATE_PROBLEM_KEY, "two problems share a key");
                }
            }
        }
        List<ObjectNode> openQuestions = sent.openQuestions() == null ? List.of() : sent.openQuestions();
        return switch (outcome) {
            case MET ->
                new MetCandidate(
                        sent.summary(), sent.explanation(), sent.citations(), sent.coverage(), example, sent.search());
            case NOT_MET ->
                new NotMetCandidate(
                        sent.summary(),
                        sent.explanation(),
                        sent.citations(),
                        sent.coverage(),
                        Objects.requireNonNull(problems),
                        example,
                        sent.search(),
                        openQuestions);
            case DOES_NOT_APPLY ->
                new DoesNotApplyCandidate(
                        sent.summary(),
                        sent.explanation(),
                        sent.citations(),
                        sent.coverage(),
                        Objects.requireNonNull(sent.inapplicability()));
            case COULD_NOT_DETERMINE ->
                new CouldNotDetermineCandidate(
                        sent.summary(),
                        sent.explanation(),
                        sent.citations(),
                        sent.coverage(),
                        sent.search(),
                        openQuestions);
        };
    }

    /** {@code readCoverage}: why the coverage's counts and completeness contradict each other, or null. */
    private static @Nullable String coverageProblem(ObjectNode coverage) {
        boolean complete = coverage.path("complete").asBoolean();
        boolean omits = coverage.has("omitted") && !coverage.path("omitted").isEmpty();
        if (complete == omits) {
            return complete ? "complete coverage lists no omission" : "incomplete coverage lists what it omitted";
        }
        if ("host".equals(coverage.path("source").asString())) {
            boolean clean = coverage.path("truncatedReads").asLong() == 0
                    && coverage.path("failedReads").asLong() == 0;
            return complete != clean ? "host coverage is complete exactly when no read was truncated or failed" : null;
        }
        long selected = coverage.path("selected").asLong();
        long checked = coverage.path("checked").asLong();
        JsonNode total = coverage.path("total");
        if (checked > selected) return "claimed coverage checked more items than it selected";
        if (!total.isNull() && selected > total.asLong()) {
            return "claimed coverage selected more items than the total";
        }
        if (complete && (checked != selected || (!total.isNull() && selected != total.asLong()))) {
            return "complete claimed coverage checks every selected item of the total";
        }
        return null;
    }

    /**
     * {@code isRepositoryPath} in {@code lib/contract.ts}: a file of a repository as a quote of code names it, relative,
     * with no empty, {@code .} or {@code ..} part, and outside the repository's {@code .git} directory.
     */
    public static boolean isRepositoryPath(String path) {
        if (path.indexOf('\\') >= 0) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.equals(".git")) return false;
        }
        return true;
    }

    /** Overlap established by the submitted coordinates; admission also resolves code source aliases. */
    private static boolean overlaps(JsonNode a, JsonNode b) {
        return a.path("sourceKind").asString().equals(b.path("sourceKind").asString())
                && a.path("sourcePath").asString().equals(b.path("sourcePath").asString())
                && Objects.equals(a.get("revision"), b.get("revision"))
                && a.path("path").asString().equals(b.path("path").asString())
                && Objects.equals(sideOf(a), sideOf(b))
                && a.path("startLine").asInt() <= b.path("endLine").asInt()
                && b.path("startLine").asInt() <= a.path("endLine").asInt();
    }

    private static @Nullable String sideOf(JsonNode citation) {
        JsonNode side = citation.get("side");
        return side == null ? null : side.asString();
    }

    /** The citation indices a basis names. */
    public static List<Integer> basisIndices(JsonNode basis) {
        return switch (basis.path("type").asString()) {
            case "introduced" -> List.of(basis.path("head").asInt());
            case "worsened" ->
                List.of(basis.path("base").asInt(), basis.path("head").asInt());
            case "duty" -> List.of(basis.path("event").asInt());
            default -> List.of();
        };
    }

    private record Superset(
            ResultOutcome outcome,
            String summary,
            String explanation,
            List<ObjectNode> citations,
            @Nullable List<CandidateProblem> problems,
            @Nullable ObjectNode example,
            @Nullable ObjectNode search,
            @Nullable ObjectNode inapplicability,
            @Nullable List<ObjectNode> openQuestions,
            ObjectNode coverage) {}

    private static Superset superset(JsonNode value) {
        ObjectNode root = object(
                value,
                "",
                SUPERSET_KEYS,
                Set.of("outcome", "summary", "explanation", "citations", "evidence", "coverage"));
        ResultOutcome outcome = enumValue(root.get("outcome"), "outcome", ResultOutcome.class);
        String summary = line(root.get("summary"), "summary", SUMMARY_CHARS);
        String explanation = bounded(root.get("explanation"), "explanation", EXPLANATION_CHARS);
        List<ObjectNode> citations = new ArrayList<>();
        for (JsonNode citation : array(root.get("citations"), "citations", 0, RESULT_CITATIONS)) {
            citations.add(citation(citation, "citations"));
        }
        List<CandidateProblem> problems = null;
        if (root.has("problems")) {
            problems = new ArrayList<>();
            int index = 0;
            for (JsonNode problem : array(root.get("problems"), "problems", 0, PROBLEMS)) {
                problems.add(problem(problem, "problems." + index++));
            }
        }
        ObjectNode example = root.has("example") ? example(root.get("example"), "example") : null;
        ObjectNode evidence = object(root.get("evidence"), "evidence", EVIDENCE_KEYS, Set.of());
        ObjectNode search = evidence.has("search")
                ? warrant(evidence.get("search"), "evidence.search", "lookedFor", "boundary")
                : null;
        ObjectNode inapplicability = evidence.has("inapplicability")
                ? warrant(evidence.get("inapplicability"), "evidence.inapplicability", "prerequisite", "ruledOutBy")
                : null;
        List<ObjectNode> openQuestions = null;
        if (evidence.has("openQuestions")) {
            openQuestions = new ArrayList<>();
            int index = 0;
            for (JsonNode question :
                    array(evidence.get("openQuestions"), "evidence.openQuestions", 1, OPEN_QUESTIONS)) {
                openQuestions.add(openQuestion(question, "evidence.openQuestions." + index++));
            }
        }
        ObjectNode coverage = coverage(root.get("coverage"), "coverage");
        return new Superset(
                outcome,
                summary,
                explanation,
                citations,
                problems,
                example,
                search,
                inapplicability,
                openQuestions,
                coverage);
    }

    private static ObjectNode citation(JsonNode value, String where) {
        ObjectNode citation = object(
                value,
                where,
                CITATION_KEYS,
                Set.of("sourceKind", "sourcePath", "path", "startLine", "endLine", "quote"));
        sourceKind(citation.get("sourceKind"), where + ".sourceKind");
        bounded(citation.get("sourcePath"), where + ".sourcePath", CandidateLimits.PATH_CHARS);
        bounded(citation.get("path"), where + ".path", CandidateLimits.PATH_CHARS);
        if (citation.has("side")) {
            String side = string(citation.get("side"), where + ".side");
            if (!side.equals("OLD") && !side.equals("NEW")) throw new Malformed(where + ".side: must be OLD or NEW");
        }
        if (citation.has("revision")) {
            bounded(citation.get("revision"), where + ".revision", CandidateLimits.VERSION_CHARS);
        }
        long start = integer(citation.get("startLine"), where + ".startLine", 1, MAX_LINE);
        long end = integer(citation.get("endLine"), where + ".endLine", 1, MAX_LINE);
        bounded(citation.get("quote"), where + ".quote", QUOTE_CHARS);
        if (end < start) throw new Malformed(where + ": endLine must be at least startLine");
        return citation.deepCopy();
    }

    private static CandidateProblem problem(JsonNode value, String where) {
        ObjectNode problem =
                object(value, where, PROBLEM_KEYS, Set.of("severity", "summary", "explanation", "citations", "basis"));
        String key = null;
        if (problem.has("key")) {
            key = string(problem.get("key"), where + ".key");
            if (!SandboxLayout.PRACTICE_SLUG.matcher(key).matches())
                throw new Malformed(where + ".key: not slug shaped");
        }
        Severity severity = enumValue(problem.get("severity"), where + ".severity", Severity.class);
        String summary = line(problem.get("summary"), where + ".summary", SUMMARY_CHARS);
        String explanation = bounded(problem.get("explanation"), where + ".explanation", PROBLEM_EXPLANATION_CHARS);
        List<ObjectNode> citations = new ArrayList<>();
        int index = 0;
        for (JsonNode citation : array(problem.get("citations"), where + ".citations", 1, CITATIONS_PER_PROBLEM)) {
            citations.add(citation(citation, where + ".citations." + index++));
        }
        ObjectNode basis = basis(problem.get("basis"), where + ".basis");
        String nextStep =
                problem.has("nextStep") ? bounded(problem.get("nextStep"), where + ".nextStep", NEXT_STEP_CHARS) : null;
        return new CandidateProblem(key, severity, summary, explanation, citations, basis, nextStep);
    }

    private static ObjectNode basis(@Nullable JsonNode value, String where) {
        if (value == null || !value.isObject()) throw new Malformed(where + ": expected an object");
        String type = value.path("type").isString() ? value.path("type").asString() : "";
        Set<String> keys = BASIS_KEYS.get(type);
        if (keys == null) throw new Malformed(where + ".type: unknown basis");
        ObjectNode basis = object(value, where, keys, keys);
        bounded(basis.get("explanation"), where + ".explanation", PROBLEM_EXPLANATION_CHARS);
        switch (type) {
            case "introduced" -> integer(basis.get("head"), where + ".head", 0, MAX_COUNT);
            case "worsened" -> {
                integer(basis.get("base"), where + ".base", 0, MAX_COUNT);
                integer(basis.get("head"), where + ".head", 0, MAX_COUNT);
            }
            case "duty" -> {
                bounded(basis.get("criterionQuote"), where + ".criterionQuote", QUOTE_CHARS);
                integer(basis.get("event"), where + ".event", 0, MAX_COUNT);
            }
            default -> {}
        }
        return basis.deepCopy();
    }

    private static ObjectNode example(JsonNode value, String where) {
        ObjectNode example = object(value, where, Set.of("citation", "note"), Set.of("citation", "note"));
        citation(example.get("citation"), where + ".citation");
        bounded(example.get("note"), where + ".note", EXAMPLE_NOTE_CHARS);
        return example.deepCopy();
    }

    private static ObjectNode warrant(JsonNode value, String where, String first, String second) {
        ObjectNode warrant =
                object(value, where, Set.of("consulted", first, second), Set.of("consulted", first, second));
        for (JsonNode consulted : array(warrant.get("consulted"), where + ".consulted", 1, CONSULTED_SOURCES)) {
            bounded(consulted, where + ".consulted", WARRANT_FIELD_CHARS);
        }
        bounded(warrant.get(first), where + "." + first, WARRANT_FIELD_CHARS);
        bounded(warrant.get(second), where + "." + second, WARRANT_FIELD_CHARS);
        return warrant.deepCopy();
    }

    private static ObjectNode openQuestion(JsonNode value, String where) {
        ObjectNode question = object(
                value,
                where,
                Set.of("openQuestion", "wouldSettleIt", "citation"),
                Set.of("openQuestion", "wouldSettleIt"));
        bounded(question.get("openQuestion"), where + ".openQuestion", OPEN_QUESTION_CHARS);
        bounded(question.get("wouldSettleIt"), where + ".wouldSettleIt", OPEN_QUESTION_CHARS);
        if (question.has("citation")) citation(question.get("citation"), where + ".citation");
        return question.deepCopy();
    }

    private static ObjectNode coverage(@Nullable JsonNode value, String where) {
        if (value == null || !value.isObject()) throw new Malformed(where + ": expected an object");
        String source = value.path("source").isString() ? value.path("source").asString() : "";
        Set<String> keys = COVERAGE_KEYS.get(source);
        if (keys == null) throw new Malformed(where + ".source: must be host or claimed");
        Set<String> required = new HashSet<>(keys);
        required.remove("omitted");
        ObjectNode coverage = object(value, where, keys, required);
        if (source.equals("host")) {
            integer(coverage.get("reads"), where + ".reads", 0, MAX_COUNT);
            integer(coverage.get("truncatedReads"), where + ".truncatedReads", 0, MAX_COUNT);
            integer(coverage.get("failedReads"), where + ".failedReads", 0, MAX_COUNT);
            int receiptIndex = 0;
            for (JsonNode receipt : array(coverage.get("receipts"), where + ".receipts", 0, CandidateLimits.RECEIPTS)) {
                readReceipt(receipt, where + ".receipts." + receiptIndex++);
            }
        } else {
            integer(coverage.get("selected"), where + ".selected", 0, MAX_COUNT);
            integer(coverage.get("checked"), where + ".checked", 0, MAX_COUNT);
            JsonNode total = coverage.get("total");
            if (total == null || !total.isNull()) integer(total, where + ".total", 0, MAX_COUNT);
        }
        if (!coverage.path("complete").isBoolean()) throw new Malformed(where + ".complete: expected a boolean");
        if (coverage.has("omitted")) {
            int index = 0;
            for (JsonNode omission : array(coverage.get("omitted"), where + ".omitted", 0, OMISSIONS)) {
                omission(omission, where + ".omitted." + index++);
            }
        }
        ObjectNode copy = coverage.deepCopy();
        // readCoverage in contract.ts keeps omitted only when it lists something.
        if (copy.path("omitted").isEmpty()) copy.remove("omitted");
        return copy;
    }

    private static void sourceKind(JsonNode value, String where) {
        try {
            new SourceKind(nonEmpty(value, where));
        } catch (IllegalArgumentException invalid) {
            throw new Malformed(where + ": invalid source kind");
        }
    }

    private static void readReceipt(JsonNode value, String where) {
        ObjectNode receipt = object(
                value,
                where,
                Set.of(
                        "sourceKind",
                        "sourcePath",
                        "path",
                        "version",
                        "status",
                        "contentDigest",
                        "startLine",
                        "endLine"),
                Set.of("sourceKind", "sourcePath", "path", "version", "status"));
        sourceKind(receipt.get("sourceKind"), where + ".sourceKind");
        for (String field : List.of("sourcePath", "path")) {
            bounded(receipt.get(field), where + "." + field, CandidateLimits.PATH_CHARS);
        }
        bounded(receipt.get("version"), where + ".version", CandidateLimits.VERSION_CHARS);
        enumValue(receipt.get("status"), where + ".status", ReadReceiptStatus.class);
        if (receipt.has("contentDigest")
                && !string(receipt.get("contentDigest"), where + ".contentDigest")
                        .matches("[0-9a-f]{64}")) {
            throw new Malformed(where + ".contentDigest: expected a SHA-256 digest");
        }
        if (receipt.has("startLine")) integer(receipt.get("startLine"), where + ".startLine", 1, MAX_LINE);
        if (receipt.has("endLine")) integer(receipt.get("endLine"), where + ".endLine", 1, MAX_LINE);
        if (receipt.has("startLine")
                && receipt.has("endLine")
                && receipt.path("endLine").asInt() < receipt.path("startLine").asInt()) {
            throw new Malformed(where + ": receipt lines are reversed");
        }
    }

    private static void omission(JsonNode value, String where) {
        ObjectNode omission =
                object(value, where, Set.of("what", "count", "reason", "items"), Set.of("what", "count", "reason"));
        line(omission.get("what"), where + ".what", SUMMARY_CHARS);
        integer(omission.get("count"), where + ".count", 0, MAX_COUNT);
        if (!OMISSION_REASONS.contains(string(omission.get("reason"), where + ".reason"))) {
            throw new Malformed(where + ".reason: unknown reason");
        }
        if (omission.has("items")) {
            for (JsonNode unit : array(omission.get("items"), where + ".items", 0, OMISSION_ITEMS)) {
                bounded(unit, where + ".items", WARRANT_FIELD_CHARS);
            }
        }
    }

    private static ObjectNode object(
            @Nullable JsonNode value, String where, Set<String> allowed, Set<String> required) {
        if (value == null || !value.isObject()) throw new Malformed(label(where) + "expected an object");
        for (String name : value.propertyNames()) {
            if (!allowed.contains(name)) throw new Malformed(label(where) + "unknown key " + name);
        }
        for (String name : required) {
            if (!value.has(name)) throw new Malformed(label(where) + "missing " + name);
        }
        return (ObjectNode) value;
    }

    private static List<JsonNode> array(@Nullable JsonNode value, String where, int min, int max) {
        if (value == null || !value.isArray()) throw new Malformed(where + ": expected an array");
        if (value.size() < min) throw new Malformed(where + ": must list at least " + min);
        if (value.size() > max) throw new Malformed(where + ": at most " + max);
        List<JsonNode> items = new ArrayList<>(value.size());
        value.forEach(items::add);
        return items;
    }

    private static String string(@Nullable JsonNode value, String where) {
        if (value == null || !value.isString()) throw new Malformed(where + ": expected a string");
        return value.asString();
    }

    private static String nonEmpty(@Nullable JsonNode value, String where) {
        String text = string(value, where);
        if (text.isEmpty()) throw new Malformed(where + ": must not be empty");
        return text;
    }

    private static String bounded(@Nullable JsonNode value, String where, int max) {
        String text = nonEmpty(value, where);
        if (text.length() > max) throw new Malformed(where + ": at most " + max + " characters");
        return text;
    }

    private static String line(@Nullable JsonNode value, String where, int max) {
        String text = bounded(value, where, max);
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) throw new Malformed(where + ": must be one line");
        return text;
    }

    /** An integer in range, as JavaScript reads one: {@code 3.0} is the integer 3. */
    private static long integer(@Nullable JsonNode value, String where, long min, long max) {
        if (value == null || !value.isNumber()) throw new Malformed(where + ": expected an integer");
        double number = value.asDouble();
        if (number != Math.rint(number) || number < min || number > max) {
            throw new Malformed(where + ": expected an integer from " + min + " to " + max);
        }
        return (long) number;
    }

    private static <E extends Enum<E>> E enumValue(@Nullable JsonNode value, String where, Class<E> type) {
        String text = string(value, where);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException unknown) {
            throw new Malformed(where + ": unknown value", unknown);
        }
    }

    private static String label(String where) {
        return where.isEmpty() ? "" : where + ": ";
    }

    private static CandidateRefusedException refuse(CandidateRefusal refusal, @Nullable String message) {
        return new CandidateRefusedException(refusal, message == null ? refusal.wireValue() : message);
    }

    /** A shape the contract does not name; turned into {@link CandidateRefusal#MALFORMED_CANDIDATE}. */
    private static final class Malformed extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        Malformed(String message) {
            super(message, null, false, false);
        }

        Malformed(String message, Throwable cause) {
            super(message, cause, false, false);
        }
    }
}
