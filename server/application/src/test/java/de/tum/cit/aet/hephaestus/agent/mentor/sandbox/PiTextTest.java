package de.tum.cit.aet.hephaestus.agent.mentor.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The expected texts are what Pi 1.0's {@code read} and {@code bash} return for the same file or output. */
class PiTextTest extends BaseUnitTest {

    private static final String OUTPUT = "/tmp/heph-out/1.log";

    @Test
    void shouldReturnTheWholeFileWhenItFitsOnOnePage() {
        PiText.Page page = PiText.read("a.txt", null, null, 2, 1, bytes("a\nb\n"));

        assertThat(page.text()).isEqualTo("a\nb\n");
    }

    @Test
    void shouldSayHowManyLinesFollowWhenTheModelLimitsThePage() {
        // Lines 2 to 4 of a file with the lines 1 to 10 and a final newline: 11 lines for Pi.
        PiText.Page page = PiText.read("a.txt", 2, 3, 10, 1, bytes("2\n3\n4\n"));

        assertThat(page.text()).isEqualTo("2\n3\n4\n\n[7 more lines in file. Use offset=5 to continue.]");
    }

    @Test
    void shouldSayHowManyLinesFollowWhenTheOneLimitedLineFillsFiftyKilobytesExactly() {
        // Pi measures the selected lines without the newline after the last one: 51,200 bytes fit.
        String line = "x".repeat(PiText.MAX_BYTES);

        PiText.Page page = PiText.read("a.txt", 1, 1, 3, PiText.MAX_BYTES, bytes(line + "\n"));

        assertThat(page.text()).isEqualTo(line + "\n\n[3 more lines in file. Use offset=2 to continue.]");
    }

    @Test
    void shouldCutThePageAtTwoThousandLinesWhenTheFileIsLonger() {
        StringBuilder chunk = new StringBuilder();
        for (int line = 1; line <= PiText.MAX_LINES + 1; line++) {
            chunk.append("line ").append(line).append('\n');
        }

        PiText.Page page = PiText.read("a.txt", null, null, 2500, 6, bytes(chunk.toString()));

        assertThat(page.text())
                .startsWith("line 1\n")
                .contains("line 2000\n\n")
                .doesNotContain("line 2001")
                .endsWith("[Showing lines 1-2000 of 2501. Use offset=2001 to continue.]");
    }

    @Test
    void shouldCutThePageAtFiftyKilobytesWhenItsLinesAreLong() {
        String line = "x".repeat(1000) + "\n";
        byte[] chunk = Arrays.copyOf(bytes(line.repeat(100)), PiText.MAX_BYTES + 1);

        PiText.Page page = PiText.read("big.txt", null, null, 100, 1000, chunk);

        assertThat(page.text())
                .endsWith("\n\n[Showing lines 1-51 of 101 (50.0KB limit). Use offset=52 to continue.]")
                .hasLineCount(53);
    }

    @Test
    void shouldPointToBashWhenTheFirstLineIsLongerThanFiftyKilobytes() {
        byte[] chunk = Arrays.copyOf(bytes("y".repeat(60_000)), PiText.MAX_BYTES + 1);

        PiText.Page page = PiText.read("big.txt", 3, null, 5, 60_000, chunk);

        assertThat(page.text())
                .isEqualTo("[Line 3 is 58.6KB, exceeds 50.0KB limit. Use bash: sed -n '3p' big.txt | head -c 51200]");
    }

    @Test
    void shouldRefuseTheOffsetWhenItIsBeyondTheEndOfTheFile() {
        PiText.Page page = PiText.read("a.txt", 5, null, 2, 0, new byte[0]);

        assertThat(page.text()).isNull();
        assertThat(page.offsetError()).isEqualTo("Offset 5 is beyond end of file (3 lines total)");
    }

    @Test
    void shouldReturnTheWholeOutputWhenItFits() {
        PiText.Tail tail = PiText.bash(1, 3, 2, bytes("hi\n"), OUTPUT);

        assertThat(tail.truncated()).isFalse();
        assertThat(tail.text()).isEqualTo("hi\n");
    }

    @Test
    void shouldKeepTheLastTwoThousandLinesAndNameTheFullOutputWhenTheOutputIsLonger() {
        StringBuilder output = new StringBuilder();
        for (int line = 1; line <= 3000; line++) {
            output.append(line).append('\n');
        }
        byte[] all = bytes(output.toString());

        PiText.Tail tail = PiText.bash(3000, all.length, 4, all, OUTPUT);

        assertThat(tail.truncated()).isTrue();
        assertThat(tail.text())
                .startsWith("1001\n")
                .endsWith("3000\n\n[Showing lines 1001-3000 of 3000. Full output: " + OUTPUT + "]");
    }

    @Test
    void shouldKeepTheEndOfTheLastLineWhenItAloneIsLongerThanFiftyKilobytes() {
        byte[] all = bytes("z".repeat(70_000));
        byte[] tail = Arrays.copyOfRange(all, all.length - (PiText.MAX_BYTES + 2), all.length);

        PiText.Tail cut = PiText.bash(0, all.length, all.length, tail, OUTPUT);

        assertThat(cut.text())
                .endsWith("\n\n[Showing last 50.0KB of line 1 (line is 68.4KB). Full output: " + OUTPUT + "]");
        assertThat(cut.text().indexOf('\n')).isEqualTo(PiText.MAX_BYTES);
    }

    @Test
    void shouldCutTheTailAtFiftyKilobytesWhenTheLastLinesAreLong() {
        String line = "w".repeat(1000) + "\n";
        byte[] all = bytes(line.repeat(100));
        byte[] tail = Arrays.copyOfRange(all, all.length - (PiText.MAX_BYTES + 2), all.length);

        PiText.Tail cut = PiText.bash(100, all.length, 1000, tail, OUTPUT);

        assertThat(cut.text()).endsWith("[Showing lines 50-100 of 100 (50.0KB limit). Full output: " + OUTPUT + "]");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
