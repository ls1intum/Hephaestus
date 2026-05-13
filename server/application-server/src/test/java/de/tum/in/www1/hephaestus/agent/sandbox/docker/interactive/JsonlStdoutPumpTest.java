package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonlStdoutPump")
class JsonlStdoutPumpTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Constructed at runtime. Java processes unicode-escape sequences (the six-character
    // backslash-u-2-0-2-8 form) BEFORE lexing, so writing the escape inside a string literal
    // would translate it into the actual codepoint and end the literal \u2014 fails to compile.
    // Code-point-via-Character.toString avoids the pre-lexer entirely.
    private static final String LINE_SEP = Character.toString(0x2028);
    private static final String PARA_SEP = Character.toString(0x2029);
    private static final int LINE_CAP = 1024;

    private record Captured(List<JsonNode> frames, List<Integer> bytes, AtomicInteger eofCalls) {}

    private Captured runPump(String input) {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter parseErrors = reg.counter("test.parse.error");
        return runPump(input, parseErrors, LINE_CAP);
    }

    private Captured runPump(String input, Counter parseErrors, int lineCap) {
        List<JsonNode> frames = new ArrayList<>();
        List<Integer> bytes = new ArrayList<>();
        AtomicInteger eofCalls = new AtomicInteger();
        JsonlStdoutPump pump = new JsonlStdoutPump(
            UUID.randomUUID(),
            new StringReader(input),
            MAPPER,
            (frame, wireBytes) -> {
                frames.add(frame);
                bytes.add(wireBytes);
            },
            ec -> eofCalls.incrementAndGet(),
            () -> 0,
            parseErrors,
            lineCap,
            Map.of()
        );
        pump.start();
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(eofCalls.get()).isEqualTo(1));
        return new Captured(frames, bytes, eofCalls);
    }

    @Test
    @DisplayName("parses well-formed JSONL frames in order")
    void parsesFrames() {
        Captured c = runPump("{\"t\":\"a\"}\n{\"t\":\"b\"}\n{\"t\":\"c\"}\n");
        assertThat(c.frames()).hasSize(3);
        assertThat(c.frames().get(0).get("t").asText()).isEqualTo("a");
        assertThat(c.frames().get(2).get("t").asText()).isEqualTo("c");
    }

    @Test
    @DisplayName("U+2028 / U+2029 inside JSON string values do NOT split the frame")
    void unicodeSeparatorsSurvive() {
        // ONE JSONL line containing both U+2028 and U+2029 inside a JSON string value.
        // BufferedReader.readLine() would mis-split on these; our \n-only pump must not.
        String line = "{\"t\":\"echo\",\"p\":\"x" + LINE_SEP + "y" + PARA_SEP + "z\"}\n";
        Captured c = runPump(line);
        assertThat(c.frames()).hasSize(1);
        String decoded = c.frames().get(0).get("p").asText();
        assertThat(decoded).contains(LINE_SEP).contains(PARA_SEP);
        // UTF-8 sanity: U+2028 / U+2029 are 3 bytes each
        assertThat(LINE_SEP.getBytes(StandardCharsets.UTF_8)).hasSize(3);
        assertThat(PARA_SEP.getBytes(StandardCharsets.UTF_8)).hasSize(3);
    }

    @Test
    @DisplayName("JSON \\n / \\r escapes inside string values decode to real LF / CR, single frame")
    void escapedNewlinesSurvive() {
        // \\n in the JSON source is two source chars (backslash, n) — NOT a real newline.
        String line = "{\"t\":\"echo\",\"p\":\"a\\nb\\rc\"}\n";
        Captured c = runPump(line);
        assertThat(c.frames()).hasSize(1);
        String decoded = c.frames().get(0).get("p").asText();
        assertThat(decoded).isEqualTo("a\nb\rc");
    }

    @Test
    @DisplayName("malformed line increments parse counter and pump continues")
    void malformedLineSurvived() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter parseErrors = reg.counter("test.parse.error");
        Captured c = runPump("{\"t\":\"a\"}\n{not json}\n{\"t\":\"c\"}\n", parseErrors, LINE_CAP);
        assertThat(c.frames()).hasSize(2);
        assertThat(c.frames().get(0).get("t").asText()).isEqualTo("a");
        assertThat(c.frames().get(1).get("t").asText()).isEqualTo("c");
        assertThat(parseErrors.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("blank lines are silently skipped (not counted as errors)")
    void blankLinesSkipped() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter parseErrors = reg.counter("test.parse.error");
        Captured c = runPump("\n\n{\"t\":\"a\"}\n\n\n{\"t\":\"b\"}\n\n", parseErrors, LINE_CAP);
        assertThat(c.frames()).hasSize(2);
        assertThat(parseErrors.count()).isZero();
    }

    @Test
    @DisplayName("oversized line is dropped (parse error counter), pump terminates")
    void oversizedLineRejected() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        Counter parseErrors = reg.counter("test.parse.error");
        // 200-char line is bigger than the 64-char cap; the pump should drop it and exit.
        String big = "{\"t\":\"" + "x".repeat(200) + "\"}\n{\"t\":\"after\"}\n";
        Captured c = runPump(big, parseErrors, 64);
        assertThat(parseErrors.count()).isGreaterThanOrEqualTo(1.0);
        // The pump exits on oversize — "after" is NOT delivered.
        assertThat(c.frames()).noneMatch(f -> "after".equals(f.get("t").asText()));
    }

    @Test
    @DisplayName("raw \\r bytes between would-be frames do NOT split: only \\n is a terminator")
    void rawCarriageReturnBetweenFramesIsNotASplitter() {
        // Stream: frame1 <CR> frame2 <LF>. BufferedReader.readLine() would split on the CR and
        // surface "frame2" separately. Our \n-only pump must instead treat the whole thing as
        // ONE line. The "b" frame must never be visible as its own emission — that's the bug
        // we're guarding against. (Jackson's readTree is lenient about trailing content after
        // the first complete value, so up to one frame may parse; never two.)
        Captured c = runPump("{\"a\":1}\r{\"b\":2}\n");
        long bFrames = c
            .frames()
            .stream()
            .filter(n -> n.has("b"))
            .count();
        assertThat(bFrames).as("\\r must not produce a second frame").isZero();
    }

    @Test
    @DisplayName("wireBytes captures exact UTF-8 length plus the terminator newline")
    void byteCounterIsExact() {
        String line = "{\"t\":\"x\"}\n";
        Captured c = runPump(line);
        assertThat(c.bytes()).hasSize(1);
        // The frame is ASCII so chars == UTF-8 bytes; +1 for the \n terminator.
        assertThat(c.bytes().get(0)).isEqualTo("{\"t\":\"x\"}".getBytes(StandardCharsets.UTF_8).length + 1);
    }
}
