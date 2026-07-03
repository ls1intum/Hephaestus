package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import com.slack.api.model.block.LayoutBlock;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChannel;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams one mentor turn into a Slack thread natively — the peer of {@code MentorSseChannel} for Slack.
 * Maps the {@link UIMessageChunk} stream onto Slack's streaming API ({@code chat.startStream} /
 * {@code chat.appendStream} / {@code chat.stopStream}).
 *
 * <p><strong>Cadence, not size.</strong> {@link #send} only buffers the incoming {@code TextDelta}s; a fixed
 * ~{@value #FLUSH_INTERVAL_MS} ms flush loop drains the buffer to Slack. That gives a fast first paint (the
 * stream opens on the first tick that has content) and a smooth, steady reveal for replies of ANY length —
 * unlike a size/newline gate, which leaves short single-paragraph replies buffered until the very end. The
 * cadence keeps us well inside {@code chat.appendStream}'s rate-limit tier, and Slack animates the text reveal
 * between appends, so per-token writes are neither needed nor desirable.
 *
 * <p><strong>Boundaries.</strong> A drain cuts at the last whitespace so a word is never split mid-stream; a
 * single unbroken token is held until it breaks (or until it grows past {@value #MAX_APPEND_CHARS}, which also
 * keeps every append under Slack's 12k {@code markdown_text} cap).
 *
 * <p><strong>Resilience.</strong> A transient Slack failure (rate limit / 5xx / transport) is re-buffered and
 * retried on the next tick — it does NOT abort the turn. Only an unambiguous "the target is gone" error
 * ({@link #GONE_ERRORS}) flips the gone flag, fires {@code onDisconnect} (so the orchestrator aborts the Pi
 * generation), and stops the loop. Persistent transient failure gives up after {@value #MAX_CONSECUTIVE_FAILURES}
 * ticks so a Slack outage never wedges a turn.
 */
public class SlackStreamingMentorChannel implements MentorChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackStreamingMentorChannel.class);

    /** Flush cadence. Slack animates between appends; ~600 ms feels live while staying inside the rate-limit tier. */
    private static final long FLUSH_INTERVAL_MS = 600;
    /** First flush shortly after the first delta, for a snappy first paint (well under the 2-minute status timeout). */
    private static final long INITIAL_DELAY_MS = 350;
    /** Cap a single append below Slack's 12k {@code markdown_text} limit; also bounds how long one giant token is held. */
    private static final int MAX_APPEND_CHARS = 8000;
    /** Give up (and abort) after this many consecutive transient failures so a Slack outage cannot wedge a turn. */
    private static final int MAX_CONSECUTIVE_FAILURES = 8;

    /** Slack error codes that mean the target is genuinely gone — everything else is treated as transient/retryable. */
    private static final Set<String> GONE_ERRORS = Set.of(
        "message_not_found",
        "channel_not_found",
        "thread_not_found",
        "cant_update_message",
        "message_deleted",
        "is_archived",
        "stream_not_found",
        "cant_stream"
    );

    private final SlackMessageService slack;
    private final long workspaceId;
    private final String channel;
    private final String threadTs;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "slack-mentor-stream");
        t.setDaemon(true);
        return t;
    });

    private final StringBuilder pending = new StringBuilder();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicBoolean clientGone = new AtomicBoolean(false);
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    private volatile Runnable disconnectHook;
    /** The delivered conversational feedback this turn raised, bound before the terminal so the buttons carry it. */
    private volatile @Nullable UUID boundFeedbackId;
    private volatile String streamTs; // null until the first drain opens the stream; only mutated on the flush thread
    private volatile ScheduledFuture<?> flushTask;
    private int consecutiveFailures; // flush-thread only

    public SlackStreamingMentorChannel(SlackMessageService slack, long workspaceId, String channel, String threadTs) {
        this.slack = slack;
        this.workspaceId = workspaceId;
        this.channel = channel;
        this.threadTs = threadTs;
    }

    @Override
    public void onDisconnect(Runnable hook) {
        this.disconnectHook = hook;
        if (clientGone.get()) {
            hook.run();
        }
    }

    @Override
    public boolean isClientGone() {
        return clientGone.get();
    }

    /**
     * Bind the conversational feedback this turn delivered so the terminal feedback buttons carry its id (enabling
     * the dispute path on a thumbs-down). Optional: an unbound turn still gets pure satisfaction thumbs. Wiring the
     * actual id from the delivery reconciler is a follow-up; the buttons render either way.
     */
    public void bindFeedback(@Nullable UUID feedbackId) {
        this.boundFeedbackId = feedbackId;
    }

    @Override
    public void startKeepAlive() {
        // Liveness while the sandbox warms up. Best-effort (assistant threads only); superseded by the first stream write.
        slack.setStatus(workspaceId, channel, threadTs, "Thinking…");
        ensureFlushing();
    }

    @Override
    public void send(UIMessageChunk chunk) {
        if (done.get() || clientGone.get()) {
            return;
        }
        if (chunk instanceof UIMessageChunk.TextDelta delta) {
            append(delta.delta());
            ensureFlushing();
        } else if (chunk instanceof UIMessageChunk.Error error) {
            // Surface mid-turn errors in the visible stream rather than dropping them.
            append("\n\n⚠️ " + safeError(error.errorText()));
            ensureFlushing();
        } else if (chunk instanceof UIMessageChunk.ToolInputStart) {
            slack.setStatus(workspaceId, channel, threadTs, "Reviewing your practice history…");
        }
        // Start/Reasoning/tool-output/Finish chunks are not part of the visible Slack stream; the orchestrator
        // drives terminals through completeWith*.
    }

    @Override
    public void completeWithDone() {
        finish(null);
    }

    @Override
    public void completeWithError(String errorText) {
        finish("\n\n⚠️ " + safeError(errorText));
    }

    @Override
    public void completeWithConflict() {
        finish("_I'm still working on your previous message — give me a moment and try again._");
    }

    @Override
    public void close() {
        // The turn always drives a completeWith* before close(); this just guarantees the stream is stopped.
        finish(null);
    }

    // --- internals ---

    private void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            pending.append(text);
        } finally {
            lock.unlock();
        }
    }

    /** Start the periodic flush loop once (idempotent); driven off {@link #startKeepAlive}/{@link #send}. */
    private void ensureFlushing() {
        if (done.get() || clientGone.get() || !flushing.compareAndSet(false, true)) {
            return;
        }
        flushTask = scheduler.scheduleWithFixedDelay(
            this::tick,
            INITIAL_DELAY_MS,
            FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /** One flush tick: drain a whitespace-aligned prefix and write it to Slack. Runs single-threaded. */
    private void tick() {
        if (done.get() || clientGone.get()) {
            return;
        }
        String toSend = drain(false);
        if (toSend != null) {
            write(toSend);
        }
    }

    /**
     * Remove and return the largest safe prefix of {@code pending}: everything up to the last whitespace (so a
     * word is never split), capped at {@value #MAX_APPEND_CHARS}. Returns {@code null} when nothing is safe to
     * send yet. {@code force} (terminal) drains everything.
     */
    private String drain(boolean force) {
        lock.lock();
        try {
            int len = pending.length();
            if (len == 0) {
                return null;
            }
            int cut = force ? len : safeCut(pending);
            if (cut <= 0) {
                return null;
            }
            if (cut > MAX_APPEND_CHARS) {
                cut = MAX_APPEND_CHARS;
            }
            String out = pending.substring(0, cut);
            pending.delete(0, cut);
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Last whitespace boundary (so a word is never split), or the whole buffer once a lone token grows past the cap. */
    private static int safeCut(CharSequence buf) {
        for (int i = buf.length() - 1; i >= 0; i--) {
            char c = buf.charAt(i);
            if (c == '\n' || c == ' ' || c == '\t') {
                return i + 1;
            }
        }
        return buf.length() >= MAX_APPEND_CHARS ? buf.length() : 0;
    }

    /** Open the stream on the first write, then append. Transient failures re-buffer and retry; "gone" aborts. */
    private void write(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            if (streamTs == null) {
                streamTs = slack.startStream(workspaceId, channel, threadTs, text);
            } else {
                slack.appendStream(workspaceId, channel, streamTs, text);
            }
            consecutiveFailures = 0;
        } catch (SlackSendException e) {
            if (isGone(e)) {
                markGone();
                return;
            }
            // Transient (rate limit / 5xx / transport): put the text back at the front and retry next tick.
            lock.lock();
            try {
                pending.insert(0, text);
            } finally {
                lock.unlock();
            }
            if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn(
                    "Slack stream giving up after {} transient failures (channel={}): {}",
                    consecutiveFailures,
                    channel,
                    e.slackError()
                );
                markGone();
            } else {
                log.debug(
                    "Slack stream append retry {} (channel={}): {}",
                    consecutiveFailures,
                    channel,
                    e.slackError()
                );
            }
        }
    }

    private void finish(String suffix) {
        if (!done.compareAndSet(false, true)) {
            return; // idempotent terminal
        }
        stopFlusher();

        String remainder;
        lock.lock();
        try {
            if (suffix != null) {
                pending.append(suffix);
            }
            remainder = pending.toString();
            pending.setLength(0);
        } finally {
            lock.unlock();
        }

        if (clientGone.get()) {
            return; // target is gone; nothing to finalize
        }
        // A turn that finishes before the first flush tick (or leaves a tail) writes here. No further tick will
        // run, so retry the terminal write inline rather than dropping it on a transient blip.
        String body = (streamTs == null && remainder.isBlank()) ? "_(the mentor produced no response)_" : remainder;
        if (!body.isBlank() || streamTs == null) {
            terminalWrite(body);
        }
        try {
            if (streamTs != null && !clientGone.get()) {
                // Attach the feedback buttons (👍/👎, bound to this turn's ts + optional feedback id) as terminal blocks.
                List<LayoutBlock> blocks = SlackFeedbackBlocks.feedbackButtons(streamTs, boundFeedbackId);
                slack.stopStream(workspaceId, channel, streamTs, blocks);
            }
        } catch (Exception e) {
            // Terminals never throw (contract). A gone recipient just means the stream is already finalized.
            log.debug("Slack stream finalize skipped (channel={}): {}", channel, e.getMessage());
        }
    }

    /** Terminal content write (open or append) with a few transient retries — the flush loop is already stopped. */
    private void terminalWrite(String text) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (streamTs == null) {
                    streamTs = slack.startStream(workspaceId, channel, threadTs, text);
                } else {
                    slack.appendStream(workspaceId, channel, streamTs, text);
                }
                return;
            } catch (SlackSendException e) {
                if (isGone(e)) {
                    markGone();
                    return;
                }
                if (attempt == 3) {
                    log.debug("Slack terminal write gave up (channel={}): {}", channel, e.slackError());
                    return;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Stop the flush loop and wait for any in-flight tick so it can never race the terminal finalize. Called only
     * from {@link #finish} (the runner thread) — never from the flush thread, which must not await itself.
     */
    private void stopFlusher() {
        ScheduledFuture<?> task = flushTask;
        if (task != null) {
            task.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    /**
     * Mark the recipient gone and fire the disconnect hook once. Runs on the flush thread, so it only cancels the
     * task (no {@code awaitTermination} — a task cannot wait for itself); {@link #finish} performs the full drain.
     */
    private void markGone() {
        ScheduledFuture<?> task = flushTask;
        if (task != null) {
            task.cancel(false);
        }
        if (clientGone.compareAndSet(false, true)) {
            Runnable hook = disconnectHook;
            if (hook != null) {
                hook.run();
            }
        }
    }

    private static boolean isGone(SlackSendException e) {
        String code = e.slackError();
        return code != null && GONE_ERRORS.contains(code);
    }

    private static String safeError(String text) {
        return (text == null || text.isBlank()) ? "The mentor hit an error." : text;
    }
}
