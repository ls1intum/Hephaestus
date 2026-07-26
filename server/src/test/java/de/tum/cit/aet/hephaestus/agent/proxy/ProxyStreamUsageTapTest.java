package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * What a streamed call contributes to billing, read off the bytes as they pass.
 *
 * <p>The behaviour under test is the whole of half M2: before this tap existed the proxy returned the
 * moment it saw an SSE body, so every streamed call recorded nothing at all. Each test below names the
 * one-token mutation it kills, because "we now look at the stream" is only worth having if it survives
 * the ways a real stream is shaped — usage in the last frame, frames split across TCP-sized buffers,
 * and streams that stop early.
 */
class ProxyStreamUsageTapTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ProxyStreamUsageTap completionsTap() {
        return new ProxyStreamUsageTap(MAPPER, false);
    }

    private static void feed(ProxyStreamUsageTap tap, String text) {
        tap.accept(text.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    class ChatCompletions {

        /**
         * The shape a chat-completions stream actually has with {@code stream_options.include_usage}:
         * many delta frames carrying {@code "usage": null}, then one final frame carrying the totals.
         *
         * <p>Kills "drop the {@code observed = parsed} assignment in handleLine": the tap then reports
         * nothing and every streamed call is billed as zero, which is the bug this class fixes.
         */
        @Test
        @DisplayName("reads the totals off the final frame, ignoring the null-usage deltas before it")
        void readsUsageFromTheFinalFrame() {
            ProxyStreamUsageTap tap = completionsTap();

            feed(tap, "data: {\"choices\":[{\"delta\":{\"content\":\"He\"}}],\"usage\":null}\n\n");
            feed(tap, "data: {\"choices\":[{\"delta\":{\"content\":\"llo\"}}],\"usage\":null}\n\n");
            feed(
                tap,
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50," +
                    "\"prompt_tokens_details\":{\"cached_tokens\":20}," +
                    "\"completion_tokens_details\":{\"reasoning_tokens\":10}}}\n\n"
            );
            feed(tap, "data: [DONE]\n\n");

            ProxyTokenUsage observed = tap.observed();
            assertThat(observed).isNotNull();
            // 100 prompt tokens of which 20 were cache reads: the input bucket is the remainder.
            assertThat(observed.billableInputTokens()).isEqualTo(80);
            assertThat(observed.cacheReadTokens()).isEqualTo(20);
            assertThat(observed.outputTokens()).isEqualTo(50);
            assertThat(observed.reasoningTokens()).isEqualTo(10);
        }

        /**
         * A DataBuffer boundary falls wherever the network puts it, including the middle of the one
         * frame that carries the money.
         *
         * <p>Kills "parse each chunk on its own instead of reassembling lines": the usage frame is
         * then two unparseable halves and the call is billed as zero.
         */
        @Test
        @DisplayName("reassembles a usage frame split across chunk boundaries")
        void reassemblesAFrameSplitAcrossChunks() {
            ProxyStreamUsageTap tap = completionsTap();

            feed(tap, "data: {\"choices\":[],\"usa");
            feed(tap, "ge\":{\"prompt_tokens\":7,\"comple");
            feed(tap, "tion_tokens\":3}}\n");

            assertThat(tap.observed()).isNotNull();
            assertThat(tap.observed().billableInputTokens()).isEqualTo(7);
            assertThat(tap.observed().outputTokens()).isEqualTo(3);
        }

        /**
         * The reported figure is cumulative for the whole response, so a provider that repeats it must
         * not be billed twice.
         *
         * <p>Kills "sum successive usage frames instead of replacing": the totals below would read 12
         * input tokens rather than 9.
         */
        @Test
        @DisplayName("a repeated usage block replaces the earlier one rather than adding to it")
        void laterUsageReplacesEarlier() {
            ProxyStreamUsageTap tap = completionsTap();

            feed(tap, "data: {\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1}}\n");
            feed(tap, "data: {\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":4}}\n");

            assertThat(tap.observed().billableInputTokens()).isEqualTo(9);
            assertThat(tap.observed().outputTokens()).isEqualTo(4);
        }

        /**
         * A stream that dies mid-flight bills what it observed — and before the terminal frame it has
         * observed nothing. Deltas are text, not token counts; estimating from them would be a guess.
         */
        @Test
        @DisplayName("a stream cut off before the usage frame reports nothing rather than a guess")
        void aTruncatedStreamReportsNothing() {
            ProxyStreamUsageTap tap = completionsTap();

            feed(tap, "data: {\"choices\":[{\"delta\":{\"content\":\"partial answ\"}}],\"usage\":null}\n\n");

            assertThat(tap.observed()).isNull();
        }

        /** Only SSE payload lines are read; the comment keep-alives and field lines around them are not. */
        @Test
        @DisplayName("non-data lines are not parsed as frames")
        void ignoresNonDataLines() {
            ProxyStreamUsageTap tap = completionsTap();

            feed(tap, ": keep-alive with the word usage in it\n");
            feed(tap, "event: {\"usage\":{\"prompt_tokens\":999}}\n");

            assertThat(tap.observed()).isNull();
        }

        /**
         * A provider that never terminates a line must cost bounded memory. After the cap trips the tap
         * resynchronises on the next newline rather than staying wedged for the rest of the stream.
         */
        @Test
        @DisplayName("an unterminated frame is abandoned, and the next frame is still read")
        void abandonsAnOversizedFrameAndResynchronises() {
            ProxyStreamUsageTap tap = completionsTap();

            String filler = "x".repeat(64 * 1024);
            for (int i = 0; i < 20; i++) {
                feed(tap, filler);
            }
            feed(tap, "\ndata: {\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n");

            assertThat(tap.observed()).isNotNull();
            assertThat(tap.observed().billableInputTokens()).isEqualTo(5);
        }
    }

    @Nested
    class ResponsesProtocol {

        /**
         * The responses API nests usage under the terminal event's {@code response} envelope, which is
         * why the tap tries both placements.
         *
         * <p>Kills "drop the {@code event.get(\"response\")} fallback in handleLine": every streamed
         * call on the responses protocol — the mentor's default — is then billed as zero.
         */
        @Test
        @DisplayName("reads usage from the response.completed envelope")
        void readsUsageFromTheCompletedEvent() {
            ProxyStreamUsageTap tap = new ProxyStreamUsageTap(MAPPER, true);

            feed(tap, "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hi\"}\n\n");
            feed(
                tap,
                "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":200," +
                    "\"output_tokens\":70,\"input_tokens_details\":{\"cached_tokens\":50}," +
                    "\"output_tokens_details\":{\"reasoning_tokens\":25}}}}\n\n"
            );

            ProxyTokenUsage observed = tap.observed();
            assertThat(observed).isNotNull();
            assertThat(observed.billableInputTokens()).isEqualTo(150);
            assertThat(observed.cacheReadTokens()).isEqualTo(50);
            assertThat(observed.outputTokens()).isEqualTo(70);
            assertThat(observed.reasoningTokens()).isEqualTo(25);
        }

        /**
         * A response the provider gave up on still burned tokens, and it reports them on the same
         * envelope — so a truncated answer is billed for what it cost, not written off.
         */
        @Test
        @DisplayName("an incomplete response is billed for the tokens it did burn")
        void billsAnIncompleteResponse() {
            ProxyStreamUsageTap tap = new ProxyStreamUsageTap(MAPPER, true);

            feed(
                tap,
                "data: {\"type\":\"response.incomplete\",\"response\":{\"usage\":{\"input_tokens\":11," +
                    "\"output_tokens\":4}}}\n\n"
            );

            assertThat(tap.observed()).isNotNull();
            assertThat(tap.observed().billableInputTokens()).isEqualTo(11);
            assertThat(tap.observed().outputTokens()).isEqualTo(4);
        }
    }
}
