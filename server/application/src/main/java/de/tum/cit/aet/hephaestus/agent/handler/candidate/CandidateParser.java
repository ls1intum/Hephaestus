package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CANDIDATE_BYTES;

import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class CandidateParser {
    private static final JsonMapper MAPPER = CandidateWireMapper.create();
    private static final ObjectReader READER = MAPPER.readerFor(CandidateDocument.class);

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

    private CandidateParser() {}

    /**
     * The candidate this value is, or why it is not one.
     *
     * @throws CandidateRefusedException with the code of the first rule the value breaks
     */
    public static Candidate parse(@Nullable JsonNode value) {
        if (value == null || !value.isObject()) {
            throw refuse(CandidateRefusal.MALFORMED_CANDIDATE, "the candidate is missing");
        }
        long bytes = javaScriptJsonBytes(value);
        if (bytes > CANDIDATE_BYTES) {
            throw refuse(
                    CandidateRefusal.MALFORMED_CANDIDATE,
                    "the candidate has " + bytes + " bytes; at most " + CANDIDATE_BYTES);
        }
        CandidateDocument sent;
        try {
            rejectExplicitNulls(value);
            sent = READER.readValue(value);
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new CandidateRefusedException(
                    CandidateRefusal.MALFORMED_CANDIDATE,
                    "The candidate does not match the result contract",
                    malformed);
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

    private static Candidate rules(CandidateDocument sent) {
        ResultOutcome outcome = sent.outcome();
        List<CandidateProblem> problems = sent.problems() == null
                ? null
                : sent.problems().stream()
                        .map(problem -> new CandidateProblem(
                                problem.key(),
                                problem.severity(),
                                problem.summary(),
                                problem.explanation(),
                                problem.citations().stream()
                                        .map(CandidateParser::node)
                                        .toList(),
                                node(problem.basis()),
                                problem.nextStep()))
                        .toList();
        List<ObjectNode> citations =
                sent.citations().stream().map(CandidateParser::node).toList();
        ObjectNode coverage = node(sent.coverage());
        if (coverage.path("omitted").isEmpty()) coverage.remove("omitted");
        ObjectNode searchNode =
                sent.evidence().search() == null ? null : node(sent.evidence().search());
        ObjectNode inapplicabilityNode = sent.evidence().inapplicability() == null
                ? null
                : node(sent.evidence().inapplicability());
        List<ObjectNode> questions = sent.evidence().openQuestions() == null
                ? null
                : sent.evidence().openQuestions().stream()
                        .map(CandidateParser::node)
                        .toList();
        boolean listsProblems = problems != null && !problems.isEmpty();
        if ((outcome == ResultOutcome.NOT_MET) != listsProblems || (problems != null && problems.isEmpty())) {
            throw refuse(
                    CandidateRefusal.OUTCOME_PROBLEM_MISMATCH,
                    outcome == ResultOutcome.NOT_MET
                            ? "a NOT_MET result lists its problems"
                            : "a " + outcome + " result lists no problem");
        }
        boolean search = searchNode != null;
        boolean inapplicability = inapplicabilityNode != null;
        boolean undecidability = questions != null;
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
        if (outcome != ResultOutcome.NOT_MET && citations.isEmpty()) {
            throw refuse(CandidateRefusal.RESULT_CITATIONS_REQUIRED, "a " + outcome + " result cites what it rests on");
        }
        boolean complete = coverage.path("complete").asBoolean();
        if (outcome == ResultOutcome.MET && !complete) {
            throw refuse(CandidateRefusal.MET_COVERAGE_INCOMPLETE, "a MET result needs complete coverage");
        }
        String coverageProblem = coverageProblem(coverage);
        if (coverageProblem != null) {
            throw refuse(CandidateRefusal.COVERAGE_INCONSISTENT, coverageProblem);
        }
        ObjectNode example = sent.example() == null ? null : node(sent.example());
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
        List<ObjectNode> openQuestions = questions == null ? List.of() : questions;
        return switch (outcome) {
            case MET -> new MetCandidate(sent.summary(), sent.explanation(), citations, coverage, example, searchNode);
            case NOT_MET ->
                new NotMetCandidate(
                        sent.summary(),
                        sent.explanation(),
                        citations,
                        coverage,
                        Objects.requireNonNull(problems),
                        example,
                        searchNode,
                        openQuestions);
            case DOES_NOT_APPLY ->
                new DoesNotApplyCandidate(
                        sent.summary(),
                        sent.explanation(),
                        citations,
                        coverage,
                        Objects.requireNonNull(inapplicabilityNode));
            case COULD_NOT_DETERMINE ->
                new CouldNotDetermineCandidate(
                        sent.summary(), sent.explanation(), citations, coverage, searchNode, openQuestions);
        };
    }

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

    // Coordinate overlap is checked here; admission also resolves source aliases.
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

    public static List<Integer> basisIndices(JsonNode basis) {
        return switch (basis.path("type").asString()) {
            case "introduced" -> List.of(basis.path("head").asInt());
            case "worsened" ->
                List.of(basis.path("base").asInt(), basis.path("head").asInt());
            case "duty" -> List.of(basis.path("event").asInt());
            default -> List.of();
        };
    }

    // Jackson creator binding represents both missing optionals and explicit null as Java null.
    // The wire contract permits JSON null only for claimed coverage's required total.
    private static void rejectExplicitNulls(JsonNode value) {
        if (value.isObject()) {
            for (Map.Entry<String, JsonNode> property : value.properties()) {
                if (property.getValue().isNull() && !property.getKey().equals("total"))
                    throw new IllegalArgumentException("Explicit null is not permitted for " + property.getKey());
                rejectExplicitNulls(property.getValue());
            }
        } else if (value.isArray()) {
            for (JsonNode item : value) rejectExplicitNulls(item);
        }
    }

    private static ObjectNode node(Object value) {
        return MAPPER.valueToTree(value);
    }

    private static CandidateRefusedException refuse(CandidateRefusal refusal, @Nullable String message) {
        return new CandidateRefusedException(refusal, message == null ? refusal.wireValue() : message);
    }
}
