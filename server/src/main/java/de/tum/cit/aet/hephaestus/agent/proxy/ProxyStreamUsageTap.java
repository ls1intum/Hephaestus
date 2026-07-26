package de.tum.cit.aet.hephaestus.agent.proxy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a streamed call's token usage out of the SSE bytes on their way to the client.
 *
 * <p>A streaming call used to contribute nothing to either the ledger or the budget gate: the proxy
 * returned as soon as it saw an SSE body, so {@code accounting.recordUsage} was never reached. That
 * made any runner that streams — and the mentor streams by design — invisible to both. This tap
 * closes that half; {@code LlmProxyController#prepareBody} closes the other by asking the provider to
 * emit usage in the first place.
 *
 * <h2>Where the usage is</h2>
 *
 * <p>An OpenAI-compatible stream reports usage exactly once, at the end, and only if asked:
 * chat-completions puts a {@code usage} object on the final chunk when the request carried
 * {@code stream_options.include_usage}; the responses API puts it on {@code response.usage} of the
 * terminal {@code response.completed} event (and of {@code response.incomplete} /
 * {@code response.failed}, which is why a truncated response is still billed for what it burned).
 * Every earlier frame carries {@code "usage": null} or no usage field at all. The tap therefore keeps
 * the LAST usage block it sees and replaces rather than sums — the reported figure is cumulative for
 * the whole response, so adding them would double-bill a provider that repeats it.
 *
 * <h2>What it guarantees on a stream that dies</h2>
 *
 * <p>{@link #observed()} is whatever had actually arrived, at any point: a stream cut off before its
 * terminal frame yields nothing to bill because nothing was ever reported, and one cut off after it
 * bills the reported total. It never estimates from the deltas it saw.
 *
 * <h2>Cost and safety</h2>
 *
 * <p>Sits on the hot path of every streamed frame, so a frame is only decoded and parsed when its raw
 * bytes contain {@code usage} at all — the thousands of text-delta frames in a turn cost one substring
 * scan each. Frames are reassembled across chunk boundaries because a DataBuffer boundary can fall
 * anywhere, including mid-line; a line that grows past {@link #MAX_LINE_BYTES} without terminating is
 * abandoned rather than buffered without limit.
 *
 * <p>Not thread-safe, and does not need to be: one instance serves one stream, whose frames arrive in
 * order on a single worker. {@link #observed()} is read by the request thread only after the streaming
 * call has returned.
 */
final class ProxyStreamUsageTap implements Consumer<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(ProxyStreamUsageTap.class);

    /** Far above any real SSE frame; this bounds a provider that never sends a newline, not a working set. */
    private static final int MAX_LINE_BYTES = 1024 * 1024;

    private static final byte[] DATA_PREFIX = "data:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] USAGE_MARKER = "usage".getBytes(StandardCharsets.US_ASCII);

    private final ObjectMapper objectMapper;
    private final boolean responsesProtocol;

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(512);

    /** Set once a line outgrows the cap; cleared at the next newline so the stream resynchronises. */
    private boolean abandoningLine;

    @Nullable
    private ProxyTokenUsage observed;

    ProxyStreamUsageTap(ObjectMapper objectMapper, boolean responsesProtocol) {
        this.objectMapper = objectMapper;
        this.responsesProtocol = responsesProtocol;
    }

    @Override
    public void accept(byte[] chunk) {
        int lineStart = 0;
        for (int i = 0; i < chunk.length; i++) {
            if (chunk[i] != '\n') {
                continue;
            }
            if (abandoningLine) {
                abandoningLine = false;
            } else {
                pending.write(chunk, lineStart, i - lineStart);
                handleLine(pending.toByteArray());
            }
            pending.reset();
            lineStart = i + 1;
        }
        if (abandoningLine) {
            return;
        }
        int remaining = chunk.length - lineStart;
        if (pending.size() + remaining > MAX_LINE_BYTES) {
            log.warn("Abandoning an SSE frame longer than {} bytes; its usage (if any) goes unread", MAX_LINE_BYTES);
            pending.reset();
            abandoningLine = true;
            return;
        }
        pending.write(chunk, lineStart, remaining);
    }

    private void handleLine(byte[] line) {
        int from = startsWith(line, DATA_PREFIX) ? DATA_PREFIX.length : -1;
        if (from < 0 || !contains(line, USAGE_MARKER)) {
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(new String(line, from, line.length - from, StandardCharsets.UTF_8));
            if (!event.isObject()) {
                return;
            }
            // Chat-completions carries usage on the event itself; the responses API nests it under the
            // `response` envelope of the terminal event. Try both so one tap covers both protocols.
            ProxyTokenUsage parsed = ProxyTokenUsage.from(event, responsesProtocol);
            if (parsed == null) {
                parsed = ProxyTokenUsage.from(event.get("response"), responsesProtocol);
            }
            if (parsed != null) {
                observed = parsed;
            }
        } catch (RuntimeException e) {
            // A frame we cannot read is a frame we do not bill. The client already has it verbatim.
            log.debug("Unparseable SSE frame while looking for usage: {}", e.toString());
        }
    }

    /** The last usage the stream reported, or {@code null} when it reported none. */
    @Nullable
    ProxyTokenUsage observed() {
        return observed;
    }

    private static boolean startsWith(byte[] line, byte[] prefix) {
        if (line.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (line[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] line, byte[] marker) {
        outer: for (int i = 0; i + marker.length <= line.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (line[i + j] != marker[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
