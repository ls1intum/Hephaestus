package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the invariants that make the Slack stream feel live AND stay robust: the reply streams in more than
 * one write (time-based coalescing, not one terminal dump), the full text is delivered exactly once in order,
 * a transient Slack failure is retried without aborting the turn, and only a genuine "gone" error disconnects.
 */
class SlackStreamingMentorChannelTest extends BaseUnitTest {

    private static final long WS = 1L;
    private static final String CH = "D123";
    private static final String THREAD = "1700000000.000100";

    private final List<String> delivered = new CopyOnWriteArrayList<>();
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger appends = new AtomicInteger();
    private final AtomicInteger stops = new AtomicInteger();

    private SlackMessageService slackThatStreamsOk() {
        SlackMessageService slack = mock(SlackMessageService.class);
        when(slack.startStream(anyLong(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            delivered.add(inv.getArgument(3));
            starts.incrementAndGet();
            return "1700000000.999999";
        });
        doAnswer(inv -> {
            delivered.add(inv.getArgument(3));
            appends.incrementAndGet();
            return null;
        })
            .when(slack)
            .appendStream(anyLong(), anyString(), anyString(), anyString());
        doAnswer(inv -> {
            stops.incrementAndGet();
            return null;
        })
            .when(slack)
            .stopStream(anyLong(), anyString(), anyString(), any());
        return slack;
    }

    private static void waitUntil(BooleanSupplier cond, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!cond.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static UIMessageChunk.TextDelta delta(String s) {
        return new UIMessageChunk.TextDelta("m1", s);
    }

    @Test
    @DisplayName("streams incrementally (>1 write) and delivers the full text once, in order")
    void streamsIncrementallyAndPreservesContent() throws InterruptedException {
        SlackMessageService slack = slackThatStreamsOk();
        var channel = new SlackStreamingMentorChannel(slack, WS, CH, THREAD);

        String[] words = { "The ", "token ", "validator ", "ships ", "without ", "any ", "unit ", "tests. " };
        // Feed across tick boundaries so the flush loop opens the stream and appends before the terminal.
        for (int i = 0; i < words.length; i++) {
            channel.send(delta(words[i]));
            if (i == 3) {
                Thread.sleep(800); // let a flush tick fire mid-stream
            }
        }
        Thread.sleep(800); // a second window so an append lands before we finish
        channel.completeWithDone();
        waitUntil(() -> stops.get() >= 1, 4000);

        assertThat(starts.get()).isEqualTo(1);
        assertThat(appends.get()).as("reply must stream in more than the initial open").isGreaterThanOrEqualTo(1);
        assertThat(stops.get()).isEqualTo(1);
        assertThat(String.join("", delivered)).isEqualTo(String.join("", words));
    }

    @Test
    @DisplayName("a transient Slack failure is retried and the turn is NOT aborted")
    void transientFailureRetriesWithoutAbort() {
        SlackMessageService slack = mock(SlackMessageService.class);
        List<String> got = Collections.synchronizedList(new java.util.ArrayList<>());
        AtomicInteger startCalls = new AtomicInteger();
        AtomicBoolean stopped = new AtomicBoolean();
        when(slack.startStream(anyLong(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            // First open is rate-limited; the loop must re-buffer and retry, not give up.
            if (startCalls.incrementAndGet() == 1) {
                throw new SlackSendException(WS, CH, "ratelimited");
            }
            got.add(inv.getArgument(3));
            return "ts";
        });
        // The fast path finalizes via startStream; appendStream may or may not be hit depending on timing.
        lenient()
            .doAnswer(inv -> {
                got.add(inv.getArgument(3));
                return null;
            })
            .when(slack)
            .appendStream(anyLong(), anyString(), anyString(), anyString());
        doAnswer(inv -> {
            stopped.set(true);
            return null;
        })
            .when(slack)
            .stopStream(anyLong(), anyString(), anyString(), any());

        var channel = new SlackStreamingMentorChannel(slack, WS, CH, THREAD);
        AtomicBoolean disconnected = new AtomicBoolean();
        channel.onDisconnect(() -> disconnected.set(true));

        channel.send(delta("hello world "));
        channel.completeWithDone();
        waitUntil(stopped::get, 5000);

        assertThat(disconnected.get()).as("a rate-limit must not be treated as a disconnect").isFalse();
        assertThat(startCalls.get()).as("startStream retried after the transient failure").isGreaterThanOrEqualTo(2);
        assertThat(String.join("", got)).contains("hello world");
    }

    @Test
    @DisplayName("a genuine 'gone' error fires the disconnect hook once")
    void goneErrorDisconnects() {
        SlackMessageService slack = mock(SlackMessageService.class);
        when(slack.startStream(anyLong(), eq(CH), anyString(), anyString())).thenThrow(
            new SlackSendException(WS, CH, "channel_not_found")
        );

        var channel = new SlackStreamingMentorChannel(slack, WS, CH, THREAD);
        AtomicInteger disconnects = new AtomicInteger();
        channel.onDisconnect(disconnects::incrementAndGet);

        channel.send(delta("anything here "));
        waitUntil(() -> disconnects.get() >= 1, 4000);
        assertThat(channel.isClientGone()).isTrue();

        channel.completeWithDone(); // terminal must not fire the hook again
        assertThat(disconnects.get()).isEqualTo(1);
    }
}
