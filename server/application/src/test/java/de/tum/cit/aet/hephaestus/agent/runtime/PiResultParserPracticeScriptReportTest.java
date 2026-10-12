package de.tum.cit.aet.hephaestus.agent.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser.PracticeScriptRunReport;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeProgramKind;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelPurpose;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelUseDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNeed;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNotRatedDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNotRatedReason;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptStopReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PiResultParserPracticeScriptReportTest extends BaseUnitTest {

    private static final Map<String, PracticeProgramKind> ADMITTED =
            Map.of("comment-quality", PracticeProgramKind.BUILTIN, "error-handling", PracticeProgramKind.CUSTOM);

    private static final String DIGEST = "0123456789abcdef".repeat(4);

    private static final String ERROR_PLACEHOLDER = "\"@@error@@\"";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PiResultParser parser;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        parser = new PiResultParser(new ObjectMapper(), meterRegistry);
    }

    private List<PracticeScriptRunReport> parse(String report) {
        return parser.parsePracticeScriptReport(report.getBytes(UTF_8), ADMITTED);
    }

    private double failures() {
        return meterRegistry
                .counter("agent.pi.result.parse.failure", "stage", "practice_script_report")
                .count();
    }

    private static String report(String... entries) {
        return report(false, entries);
    }

    private static String report(boolean truncated, String... entries) {
        return "{\"schemaVersion\":2,\"truncated\":" + truncated + ",\"practices\":[" + String.join(",", entries)
                + "]}";
    }

    /**
     * An entry inside the contract for a practice of {@link #ADMITTED}: its admitted program, no models, no calls and
     * no reads. A practice that is not admitted gets the built-in program.
     */
    private static ObjectNode entry(String slug, String status, @Nullable String stopReason) {
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("practiceSlug", slug);
        ObjectNode program = entry.putObject("program");
        if (ADMITTED.get(slug) == PracticeProgramKind.CUSTOM) {
            program.put("kind", "custom");
            program.put("digest", DIGEST);
        } else {
            program.put("kind", "builtin");
            program.putNull("digest");
        }
        entry.putArray("models");
        entry.put("status", status);
        if (stopReason == null) {
            entry.putNull("stopReason");
        } else {
            entry.put("stopReason", stopReason);
        }
        ObjectNode calls = entry.putObject("calls");
        PiResultParser.CALL_KINDS.forEach(kind -> calls.put(kind, 0));
        ObjectNode coverage = entry.putObject("coverageReached");
        Stream.of("reads", "truncatedReads", "failedReads").forEach(count -> coverage.put(count, 0));
        if ("not-reached".equals(status)) {
            entry.putNull("durationMs");
        } else {
            entry.put("durationMs", 10);
        }
        return entry;
    }

    private static PracticeScriptRunReport run(List<PracticeScriptRunReport> runs, String slug) {
        return runs.stream()
                .filter(run -> run.practiceSlug().equals(slug))
                .findFirst()
                .orElseThrow();
    }

    private static String ok(String slug) {
        return entry(slug, "ok", null).toString();
    }

    /** A failed entry whose error is written with every character outside printable ASCII escaped. */
    private static String failedWithError(String slug, String error) {
        return entry(slug, "failed", "error")
                .put("error", "@@error@@")
                .toString()
                .replace(ERROR_PLACEHOLDER, asciiJson(error));
    }

    /** A JSON string with every character outside printable ASCII escaped, so an unpaired surrogate survives. */
    private static String asciiJson(String text) {
        return text.chars()
                .mapToObj(c ->
                        c >= ' ' && c <= '~' && c != '"' && c != '\\' ? Character.toString(c) : "\\u%04x".formatted(c))
                .collect(Collectors.joining("", "\"", "\""));
    }

    /** The shared fixture that {@code lib/contract.test.ts} reads too: seven admitted practices of every status. */
    @Test
    void shouldReadEveryAdmittedPracticeWhenTheSharedExecutionReportIsParsed() throws IOException {
        byte[] fixture;
        try (InputStream in = getClass().getResourceAsStream("/practice-results/execution-report.json")) {
            fixture = Objects.requireNonNull(in, "practice-results/execution-report.json")
                    .readAllBytes();
        }
        Map<String, PracticeProgramKind> admitted = Map.of(
                "avoids-insecure-defaults-and-over-broad-permissions", PracticeProgramKind.CUSTOM,
                "commit-subjects-explain-each-change", PracticeProgramKind.BUILTIN,
                "handles-errors-instead-of-swallowing-them", PracticeProgramKind.CUSTOM,
                "leaves-the-code-clean-with-intent-revealing-comments", PracticeProgramKind.BUILTIN,
                "makes-ui-accessible-by-default", PracticeProgramKind.CUSTOM,
                "ships-tests-with-the-change", PracticeProgramKind.CUSTOM,
                "validates-and-escapes-untrusted-input", PracticeProgramKind.BUILTIN);

        List<PracticeScriptRunReport> runs = parser.parsePracticeScriptReport(fixture, admitted);

        assertThat(failures()).isZero();
        assertThat(runs)
                .extracting(
                        PracticeScriptRunReport::practiceSlug,
                        PracticeScriptRunReport::status,
                        PracticeScriptRunReport::programKind,
                        PracticeScriptRunReport::stopReason)
                .containsExactly(
                        tuple(
                                "avoids-insecure-defaults-and-over-broad-permissions",
                                PracticeScriptRunStatus.SKIPPED,
                                PracticeProgramKind.CUSTOM,
                                PracticeScriptStopReason.UNAVAILABLE),
                        tuple(
                                "commit-subjects-explain-each-change",
                                PracticeScriptRunStatus.NOT_REACHED,
                                PracticeProgramKind.BUILTIN,
                                PracticeScriptStopReason.DEADLINE),
                        tuple(
                                "handles-errors-instead-of-swallowing-them",
                                PracticeScriptRunStatus.OK,
                                PracticeProgramKind.CUSTOM,
                                null),
                        tuple(
                                "leaves-the-code-clean-with-intent-revealing-comments",
                                PracticeScriptRunStatus.OK,
                                PracticeProgramKind.BUILTIN,
                                null),
                        tuple(
                                "makes-ui-accessible-by-default",
                                PracticeScriptRunStatus.OK,
                                PracticeProgramKind.CUSTOM,
                                null),
                        tuple(
                                "ships-tests-with-the-change",
                                PracticeScriptRunStatus.FAILED,
                                PracticeProgramKind.CUSTOM,
                                PracticeScriptStopReason.INVALID_PROGRAM),
                        tuple(
                                "validates-and-escapes-untrusted-input",
                                PracticeScriptRunStatus.OK,
                                PracticeProgramKind.BUILTIN,
                                null));
        assertThat(runs)
                .extracting(PracticeScriptRunReport::status)
                .doesNotContain(PracticeScriptRunStatus.NOT_FINISHED);
        PracticeScriptRunReport custom = run(runs, "handles-errors-instead-of-swallowing-them");
        assertThat(custom.programDigest())
                .isEqualTo("b71c3e9a5d2f8046c1e7a9b3d5f2e8c6a4b1d7f3e9c2a5b8d0f6e1c4a7b3d9e2");
        assertThat(custom.calls())
                .isEqualTo(Map.of("review", 1, "chat", 0, "decision", 0, "embedding", 0, "reranking", 0));
        assertThat(custom.coverageReached()).isEqualTo(Map.of("reads", 9, "truncatedReads", 1, "failedReads", 0));
        assertThat(custom.durationMs()).isEqualTo(61234);
        assertThat(run(runs, "leaves-the-code-clean-with-intent-revealing-comments")
                        .programDigest())
                .isNull();
        assertThat(run(runs, "commit-subjects-explain-each-change").durationMs())
                .isNull();
        assertThat(run(runs, "avoids-insecure-defaults-and-over-broad-permissions")
                        .models())
                .containsExactly(new PracticeScriptModelUseDTO(
                        PracticeScriptModelPurpose.PRACTICE_DECISION, PracticeScriptNeed.REQUIRED, false, List.of()));
        assertThat(run(runs, "ships-tests-with-the-change").error())
                .startsWith("This practice script uses the retired leads contract (meta.version).");
    }

    @Test
    void shouldReadEachModelWithItsPurposeNeedAndReasonsNotRatedWhenTheProgramDeclaredThem() {
        ObjectNode decision = entry("comment-quality", "ok", null);
        decision.set("models", MAPPER.readTree("""
                [{"slot":"decision","need":"required","bound":true,"notRated":{"too-large":2,"deadline":5,"budget":0}},
                 {"slot":"chat","need":"optional","bound":true,"notRated":{}}]"""));

        List<PracticeScriptRunReport> runs = parse(report(decision.toString(), ok("error-handling")));

        assertThat(runs)
                .extracting(PracticeScriptRunReport::models)
                .containsExactly(
                        List.of(
                                new PracticeScriptModelUseDTO(
                                        PracticeScriptModelPurpose.PRACTICE_DECISION,
                                        PracticeScriptNeed.REQUIRED,
                                        true,
                                        // In the declared order of the reasons; a zero count is left out.
                                        List.of(
                                                new PracticeScriptNotRatedDTO(PracticeScriptNotRatedReason.DEADLINE, 5),
                                                new PracticeScriptNotRatedDTO(
                                                        PracticeScriptNotRatedReason.TOO_LARGE, 2))),
                                new PracticeScriptModelUseDTO(
                                        PracticeScriptModelPurpose.PRACTICE_REVIEW,
                                        PracticeScriptNeed.OPTIONAL,
                                        true,
                                        List.of())),
                        List.of());
        assertThat(runs)
                .extracting(
                        PracticeScriptRunReport::programKind,
                        PracticeScriptRunReport::programDigest,
                        PracticeScriptRunReport::durationMs)
                .containsExactly(
                        tuple(PracticeProgramKind.BUILTIN, null, 10), tuple(PracticeProgramKind.CUSTOM, DIGEST, 10));
        assertThat(failures()).isZero();
    }

    @Test
    void shouldKeepTheFirstLineOfAFailedProgramsErrorCutToItsLimit() {
        // Characters outside the BMP count once each, as PostgreSQL counts them.
        String longLine = "TypeError: " + "🙂".repeat(PiResultParser.PRACTICE_SCRIPT_ERROR_MAX_LENGTH);
        List<PracticeScriptRunReport> runs = parse(report(
                failedWithError("comment-quality", longLine + "\n    at decide (script.ts:4:9)"),
                failedWithError("error-handling", "Cannot read properties of undefined")));

        assertThat(runs)
                .extracting(PracticeScriptRunReport::error)
                .containsExactly(
                        longLine.substring(
                                0, longLine.offsetByCodePoints(0, PiResultParser.PRACTICE_SCRIPT_ERROR_MAX_LENGTH)),
                        "Cannot read properties of undefined");
        assertThat(failures()).isZero();
    }

    /**
     * The report writer reads the same cases, so the writer and this parser keep one rule for the error line.
     */
    @Test
    void shouldKeepTheErrorLineThatTheReportWriterKeepsWhenBothReadTheSameError() throws IOException {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/agent/practice-script-error-lines.json")) {
            cases = new ObjectMapper()
                    .readTree(Objects.requireNonNull(in, "agent/practice-script-error-lines.json")
                            .readAllBytes());
        }
        List<String> slugs = IntStream.range(0, cases.size())
                .mapToObj(index -> "case-%02d".formatted(index))
                .toList();
        String report = report(IntStream.range(0, cases.size())
                .mapToObj(index -> failedWithError(
                        slugs.get(index), cases.get(index).path("error").asString()))
                .toArray(String[]::new));

        List<PracticeScriptRunReport> runs = parser.parsePracticeScriptReport(
                report.getBytes(UTF_8),
                slugs.stream().collect(Collectors.toMap(slug -> slug, slug -> PracticeProgramKind.BUILTIN)));

        assertThat(runs)
                .extracting(PracticeScriptRunReport::practiceSlug, PracticeScriptRunReport::error)
                .containsExactlyElementsOf(IntStream.range(0, cases.size())
                        .mapToObj(index -> tuple(
                                slugs.get(index),
                                cases.get(index).path("line").isString()
                                        ? cases.get(index).path("line").asString()
                                        : null))
                        .toList());
        assertThat(failures()).isZero();
    }

    /**
     * The run is written in the attempt's terminal transaction, so a value that PostgreSQL refuses would fail the
     * whole review. A control character becomes a space; a line with an unpaired surrogate is left out.
     */
    @Test
    void shouldKeepOnlyTextPostgresqlCanStoreWhenTheErrorHoldsANulOrAnUnpairedSurrogate() {
        List<PracticeScriptRunReport> runs = parse(report(
                failedWithError("comment-quality", "a\u0000b\tc"),
                failedWithError("error-handling", "bad \uD800 thing")));

        assertThat(runs)
                .extracting(PracticeScriptRunReport::status, PracticeScriptRunReport::error)
                .containsExactly(
                        tuple(PracticeScriptRunStatus.FAILED, "a b c"), tuple(PracticeScriptRunStatus.FAILED, null));
        assertThat(failures()).isZero();
    }

    @Test
    void shouldKeepHowLongAProgramRanWhenTheRunnerSaysAndLeaveItOutOtherwise() {
        List<PracticeScriptRunReport> runs = parse(report(
                entry("comment-quality", "ok", null).put("durationMs", 4200).toString(),
                entry("error-handling", "skipped", "unavailable")
                        .put("durationMs", -1)
                        .toString()));

        assertThat(runs).extracting(PracticeScriptRunReport::durationMs).containsExactly(4200, null);
        // A program that did not start took no time, whatever the runner wrote.
        assertThat(parse(report(entry("comment-quality", "not-reached", "deadline")
                        .put("durationMs", 40)
                        .toString())))
                .filteredOn(run -> run.practiceSlug().equals("comment-quality"))
                .extracting(PracticeScriptRunReport::durationMs)
                .containsOnlyNulls();
        assertThat(failures()).isZero();
    }

    /** The error only explains a failure, so a run that did not fail never carries one. */
    @ParameterizedTest
    @CsvSource({"ok,", "skipped,unavailable", "not-reached,deadline"})
    void shouldLeaveTheErrorOutWhenTheProgramDidNotFail(String status, @Nullable String stopReason) {
        String entry = entry("comment-quality", status, stopReason)
                .put("error", "Something broke")
                .toString();

        assertThat(parse(report(entry)).getFirst().error()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"  \"", "42", "{}", "null"})
    void shouldKeepAFailedRunWithoutAnErrorWhenTheErrorIsNotOneLineOfText(String error) {
        String entry = entry("comment-quality", "failed", "error")
                .put("error", "@@error@@")
                .toString()
                .replace(ERROR_PLACEHOLDER, error);

        List<PracticeScriptRunReport> runs = parse(report(entry));

        assertThat(runs.getFirst().status()).isEqualTo(PracticeScriptRunStatus.FAILED);
        assertThat(runs.getFirst().error()).isNull();
        assertThat(failures()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ok,,OK", "skipped,unavailable,SKIPPED", "failed,error,FAILED", "not-reached,deadline,NOT_REACHED"})
    void shouldMapEachRunnerStatusWhenTheReportNamesIt(
            String wire, @Nullable String stopReason, PracticeScriptRunStatus status) {
        assertThat(parse(report(entry("comment-quality", wire, stopReason).toString())))
                .extracting(PracticeScriptRunReport::practiceSlug, PracticeScriptRunReport::status)
                .contains(tuple("comment-quality", status));
        assertThat(failures()).isZero();
    }

    @ParameterizedTest
    @EnumSource(PracticeScriptStopReason.class)
    void shouldMapEachStopReasonWhenAFailedRunNamesIt(PracticeScriptStopReason stopReason) {
        assertThat(parse(report(
                        entry("comment-quality", "failed", stopReason.wire()).toString())))
                .extracting(PracticeScriptRunReport::practiceSlug, PracticeScriptRunReport::stopReason)
                .contains(tuple("comment-quality", stopReason));
        assertThat(failures()).isZero();
    }

    @Test
    void shouldMapEachModelSlotToThePurposeThatServesItWhenTheProgramDeclaredIt() {
        ObjectNode entry = entry("comment-quality", "skipped", "unavailable");
        entry.set("models", MAPPER.readTree("""
                [{"slot":"embedding","need":"optional","bound":false,"notRated":{"unavailable":4}},
                 {"slot":"reranking","need":"required","bound":false,"notRated":{}}]"""));

        assertThat(Objects.requireNonNull(
                        parse(report(entry.toString())).getFirst().models()))
                .extracting(PracticeScriptModelUseDTO::purpose, PracticeScriptModelUseDTO::bound)
                .containsExactly(
                        tuple(PracticeScriptModelPurpose.PRACTICE_EMBEDDING, false),
                        tuple(PracticeScriptModelPurpose.PRACTICE_RERANKING, false));
    }

    /**
     * The practices module cannot name {@link AgentPurpose}, so it mirrors the purposes of the practice script slots. A
     * purpose without its mirror leaves its slot unknown to the parser, which then drops every entry that uses it.
     */
    @Test
    void shouldMirrorEveryAgentPurposeOfAPracticeScriptSlotWhenAPurposeIsAdded() {
        assertThat(Arrays.stream(PracticeScriptModelPurpose.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.concat(Stream.of(AgentPurpose.PRACTICE_REVIEW), AgentPurpose.practiceScript().stream())
                                .map(Enum::name)
                                .toList());
    }

    @Test
    void shouldRecordAnAdmittedPracticeAsNotFinishedWhenAWholeReportDoesNotNameIt() {
        List<PracticeScriptRunReport> runs = parse(report(ok("comment-quality")));

        assertThat(runs)
                .contains(new PracticeScriptRunReport(
                        "error-handling",
                        PracticeScriptRunStatus.NOT_FINISHED,
                        PracticeProgramKind.CUSTOM,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        assertThat(failures()).isZero();
    }

    /** A truncated report left practices out for its size, not because they did not finish. */
    @Test
    void shouldRecordNoRunForAnAdmittedPracticeWhenATruncatedReportDoesNotNameIt() {
        List<PracticeScriptRunReport> runs = parse(report(true, ok("comment-quality")));

        assertThat(runs).extracting(PracticeScriptRunReport::practiceSlug).containsExactly("comment-quality");
        assertThat(failures()).isZero();
    }

    @Test
    void shouldDropAnEntryWhenTheJobDidNotAdmitItsPractice() {
        List<PracticeScriptRunReport> runs =
                parse(report(ok("comment-quality"), ok("error-handling"), ok("not-admitted")));

        assertThat(runs).extracting(PracticeScriptRunReport::practiceSlug).doesNotContain("not-admitted");
        assertThat(failures()).isEqualTo(1d);
    }

    /** Each case puts one entry for {@code comment-quality}, a built-in practice, outside the contract. */
    static Stream<Consumer<ObjectNode>> entriesOutsideTheContract() {
        return Stream.<Consumer<ObjectNode>>of(
                entry -> entry.put("status", "crashed"),
                // The program kind the runner ran is not the one the job admitted.
                entry -> entry.set("program", MAPPER.readTree("{\"kind\":\"custom\",\"digest\":\"" + DIGEST + "\"}")),
                entry -> entry.set("program", MAPPER.readTree("{\"kind\":\"builtin\",\"digest\":\"" + DIGEST + "\"}")),
                entry -> entry.set("program", MAPPER.readTree("{\"kind\":\"script\",\"digest\":null}")),
                entry -> entry.remove("program"),
                // Ok exactly when the program stopped for no reason.
                entry -> entry.put("stopReason", "error"),
                entry -> entry.put("status", "failed"),
                entry -> entry.put("status", "failed").put("stopReason", "bored"),
                // A program that did not start made no call.
                entry -> entry.put("status", "not-reached")
                        .put("stopReason", "deadline")
                        .set(
                                "calls",
                                MAPPER.readTree(
                                        "{\"review\":1,\"chat\":0,\"decision\":0,\"embedding\":0,\"reranking\":0}")),
                entry -> entry.remove("calls"),
                entry -> entry.set(
                        "calls",
                        MAPPER.readTree("{\"review\":-1,\"chat\":0,\"decision\":0,\"embedding\":0,\"reranking\":0}")),
                entry -> entry.set(
                        "calls",
                        MAPPER.readTree(
                                "{\"review\":0,\"chat\":0,\"decision\":0,\"embedding\":0,\"reranking\":0,\"vision\":0}")),
                entry -> entry.remove("coverageReached"),
                entry -> entry.set(
                        "coverageReached", MAPPER.readTree("{\"reads\":\"2\",\"truncatedReads\":0,\"failedReads\":0}")),
                entry -> entry.remove("models"),
                entry -> entry.set("models", MAPPER.readTree("{}")),
                entry -> entry.putNull("models"),
                entry -> entry.set(
                        "models",
                        MAPPER.readTree(
                                "[{\"slot\":\"vision\",\"need\":\"required\",\"bound\":true,\"notRated\":{}}]")),
                entry -> entry.set(
                        "models",
                        MAPPER.readTree(
                                "[{\"slot\":\"decision\",\"need\":\"nice-to-have\",\"bound\":true,\"notRated\":{}}]")),
                entry -> entry.set(
                        "models",
                        MAPPER.readTree(
                                "[{\"slot\":\"decision\",\"need\":\"required\",\"bound\":\"yes\",\"notRated\":{}}]")),
                entry -> entry.set(
                        "models",
                        MAPPER.readTree(
                                "[{\"slot\":\"decision\",\"need\":\"required\",\"bound\":true,\"notRated\":{\"bored\":1}}]")),
                entry -> entry.set(
                        "models",
                        MAPPER.readTree("[{\"slot\":\"decision\",\"need\":\"required\",\"bound\":true,\"notRated\":{}},"
                                + "{\"slot\":\"decision\",\"need\":\"optional\",\"bound\":true,\"notRated\":{}}]")),
                // The retired leads report.
                entry -> entry.put("leads", 2));
    }

    @ParameterizedTest
    @MethodSource("entriesOutsideTheContract")
    void shouldDropAnEntryWithoutClaimingTheProgramDidNotFinishWhenItIsOutsideTheContract(Consumer<ObjectNode> defect) {
        ObjectNode entry = entry("comment-quality", "ok", null);
        defect.accept(entry);

        List<PracticeScriptRunReport> runs = parse(report(entry.toString(), ok("error-handling")));

        assertThat(runs).extracting(PracticeScriptRunReport::practiceSlug).containsExactly("error-handling");
        assertThat(failures()).isEqualTo(1d);
    }

    @Test
    void shouldDropTheEntryWhenACustomProgramNamesNoDigest() {
        ObjectNode entry = entry("error-handling", "ok", null);
        entry.set("program", MAPPER.readTree("{\"kind\":\"custom\",\"digest\":null}"));

        List<PracticeScriptRunReport> runs = parse(report(ok("comment-quality"), entry.toString()));

        assertThat(runs).extracting(PracticeScriptRunReport::practiceSlug).containsExactly("comment-quality");
        assertThat(failures()).isEqualTo(1d);
    }

    @Test
    void shouldDropEveryEntryOfASlugWhenTheReportNamesItTwice() {
        List<PracticeScriptRunReport> runs =
                parse(report(ok("comment-quality"), failedWithError("comment-quality", "boom"), ok("error-handling")));

        assertThat(runs).extracting(PracticeScriptRunReport::practiceSlug).containsExactly("error-handling");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{\"schemaVersion\":2,\"practices\":[],\"truncated\":false} trailing",
                "{\"schemaVersion\":2,\"practices\":{},\"truncated\":false}",
                "{\"schemaVersion\":2,\"practices\":[]}",
                "{\"schemaVersion\":2,\"practices\":[],\"truncated\":\"no\"}",
                "{\"practices\":[],\"truncated\":false}",
                "{\"schemaVersion\":1,\"practices\":[],\"truncated\":false}",
                "{\"schemaVersion\":\"2\",\"practices\":[],\"truncated\":false}",
                "[]",
                "{}"
            })
    void shouldRecordNothingWhenTheReportIsInvalid(String report) {
        assertThat(parse(report)).isEmpty();
        assertThat(failures()).isEqualTo(1d);
    }

    @Test
    void shouldRecordNothingWhenThereIsNoReport() {
        assertThat(parser.parsePracticeScriptReport(null, ADMITTED)).isEmpty();
        assertThat(parser.parsePracticeScriptReport(new byte[0], ADMITTED)).isEmpty();
        assertThat(failures()).isZero();
    }

    @Test
    void shouldRefuseTheReportWhenItIsLargerThanItsLimit() {
        String entries = report(ok("comment-quality"));
        String atLimit = entries + " ".repeat(PiResultParser.PRACTICE_SCRIPT_REPORT_MAX_BYTES - entries.length());

        assertThat(parse(atLimit)).hasSize(ADMITTED.size());
        assertThat(parse(atLimit + " ")).isEmpty();
        assertThat(failures()).isEqualTo(1d);
    }

    @Test
    void shouldRefuseTheReportWhenItHasMoreEntriesThanItsLimit() {
        Map<String, PracticeProgramKind> admitted = IntStream.rangeClosed(
                        0, PiResultParser.PRACTICE_SCRIPT_REPORT_MAX_ENTRIES)
                .mapToObj(i -> "p-" + i)
                .collect(Collectors.toMap(slug -> slug, slug -> PracticeProgramKind.BUILTIN));
        // Short entries, so that the entry limit and not the size limit refuses the larger report.
        Function<String, String> shortEntry = slug -> """
                {"practiceSlug":"%s","program":{"kind":"builtin","digest":null},"models":[],"status":"ok",\
                "stopReason":null,"calls":{"review":0,"chat":0,"decision":0,"embedding":0,"reranking":0},\
                "coverageReached":{"reads":0,"truncatedReads":0,"failedReads":0}}""".formatted(slug);
        String atLimit = report(admitted.keySet().stream()
                .sorted()
                .limit(PiResultParser.PRACTICE_SCRIPT_REPORT_MAX_ENTRIES)
                .map(shortEntry)
                .toArray(String[]::new));
        String overLimit = report(admitted.keySet().stream().map(shortEntry).toArray(String[]::new));

        assertThat(parser.parsePracticeScriptReport(atLimit.getBytes(UTF_8), admitted))
                .hasSize(admitted.size());
        assertThat(parser.parsePracticeScriptReport(overLimit.getBytes(UTF_8), admitted))
                .isEmpty();
    }
}
