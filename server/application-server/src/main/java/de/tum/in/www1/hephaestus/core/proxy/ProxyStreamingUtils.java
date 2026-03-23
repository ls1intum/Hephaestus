package de.tum.in.www1.hephaestus.core.proxy;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Shared utilities for HTTP proxy controllers that need SSE streaming support.
 *
 * <p>Extracted from {@code MentorProxyController} to be reused by the LLM proxy and
 * any future proxy endpoints. All methods are stateless and thread-safe.
 */
public final class ProxyStreamingUtils {

    private static final Logger log = LoggerFactory.getLogger(ProxyStreamingUtils.class);

    /** HTTP hop-by-hop headers that must not be forwarded through a proxy (lowercase for case-insensitive matching). */
    private static final Set<String> HOP_BY_HOP_HEADERS_LOWER = Set.of(
        HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
        "keep-alive",
        HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT),
        HttpHeaders.PROXY_AUTHORIZATION.toLowerCase(Locale.ROOT),
        HttpHeaders.TE.toLowerCase(Locale.ROOT),
        HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
        HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
        HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT)
    );

    private ProxyStreamingUtils() {}

    /**
     * Filter hop-by-hop headers from a set of HTTP headers.
     * Comparison is case-insensitive per RFC 7230 Section 6.1.
     *
     * @param headers the original headers
     * @return a new {@link HttpHeaders} instance with hop-by-hop headers removed
     */
    public static HttpHeaders filterHopByHopHeaders(HttpHeaders headers) {
        // Build dynamic set: fixed RFC list + any headers named in Connection header value
        Set<String> toStrip = new HashSet<>(HOP_BY_HOP_HEADERS_LOWER);
        List<String> connectionValues = headers.get(HttpHeaders.CONNECTION);
        if (connectionValues != null) {
            for (String val : connectionValues) {
                for (String token : val.split(",")) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        toStrip.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        HttpHeaders filtered = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (!toStrip.contains(name.toLowerCase(Locale.ROOT))) {
                filtered.put(name, values);
            }
        });
        return filtered;
    }

    /**
     * Consume an upstream {@link ClientResponse}, inspecting Content-Type to decide
     * whether to buffer (non-SSE) or return a streaming Flux (SSE).
     *
     * <p>Designed as the callback for {@code WebClient.exchangeToMono()}. For non-SSE
     * responses, the body is consumed inside this callback per the WebClient contract.
     * For SSE, the Flux is returned for streaming to the client.
     *
     * @param clientResp the upstream response
     * @return an {@link UpstreamResult} containing either a buffered body or an SSE Flux
     */
    public static Mono<UpstreamResult> consumeResponse(ClientResponse clientResp) {
        HttpHeaders rh = filterHopByHopHeaders(clientResp.headers().asHttpHeaders());
        int status = clientResp.statusCode().value();
        MediaType ct = clientResp.headers().contentType().orElse(null);
        boolean isSse = ct != null && ct.isCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        if (isSse) {
            // SSE: eagerly subscribe to the body Flux via replay().autoConnect(0).
            // Without this, Mono.just() completes immediately and WebClient's exchangeToMono
            // auto-releases the unconsumed response body, causing empty SSE streams.
            Flux<DataBuffer> replayed = clientResp.bodyToFlux(DataBuffer.class).replay().autoConnect(0);
            return Mono.just(new UpstreamResult(status, rh, null, replayed));
        } else {
            // Non-SSE: consume body inside callback as required by WebClient contract.
            return clientResp
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(bytes -> new UpstreamResult(status, rh, bytes, null));
        }
    }

    /** Default timeout for SSE streaming — slightly above typical WebClient responseTimeout. */
    private static final Duration DEFAULT_SSE_TIMEOUT = Duration.ofSeconds(310);

    /**
     * Stream SSE data directly to the {@link HttpServletResponse} output stream.
     *
     * <p>Bypasses Spring MVC's message converters which do not properly handle SSE
     * streaming with preset Content-Type headers. Uses {@code publishOn(boundedElastic)}
     * to avoid blocking the Netty event loop thread with servlet I/O. Blocks until the
     * stream completes, the client disconnects, or the timeout is reached.
     *
     * @param dataFlux    reactive stream of data buffers from upstream
     * @param respHeaders headers to copy to the response
     * @param response    the servlet response to write to
     * @param statusCode  HTTP status code from upstream
     */
    public static void streamSseToResponse(
        Flux<DataBuffer> dataFlux,
        HttpHeaders respHeaders,
        HttpServletResponse response,
        int statusCode
    ) {
        try {
            response.setStatus(statusCode);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding("UTF-8");

            // Disable caching and reverse-proxy buffering for SSE
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");
            response.setHeader("X-Accel-Buffering", "no");

            // Copy upstream headers (excluding ones we set ourselves)
            respHeaders.forEach((name, values) -> {
                if (
                    !HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) &&
                    !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name) &&
                    !HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name) &&
                    !HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name)
                ) {
                    for (String value : values) {
                        response.addHeader(name, value);
                    }
                }
            });

            response.flushBuffer();
            OutputStream outputStream = response.getOutputStream();

            dataFlux
                // Release any prefetched DataBuffers on cancellation/error to prevent native memory leaks
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                // Move blocking servlet I/O off the Netty event loop thread
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        outputStream.write(bytes);
                        outputStream.flush();
                    } catch (IOException e) {
                        log.debug("Client disconnected during SSE streaming: {}", e.getMessage());
                        throw new StreamingException("Client disconnected", e);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .doOnError(e -> {
                    if (!(e instanceof StreamingException)) {
                        log.debug("SSE stream error: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        outputStream.flush();
                    } catch (IOException e) {
                        log.debug("Error flushing final SSE output: {}", e.getMessage());
                    }
                })
                .blockLast(DEFAULT_SSE_TIMEOUT); // Safe: servlet thread, not Netty event loop
        } catch (IOException e) {
            log.debug("SSE streaming initialization failed: {}", e.getMessage());
        } catch (StreamingException e) {
            log.debug("SSE streaming terminated: {}", e.getMessage());
        } catch (Exception e) {
            // Catches Netty ReadTimeoutException, PrematureCloseException, scheduler rejection, etc.
            log.warn("SSE streaming failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    /**
     * Holds the upstream response data after consuming it inside the reactive pipeline.
     *
     * <p>Per the Spring WebClient contract, non-SSE response bodies must be consumed inside
     * {@code exchangeToMono}. For SSE, the body is returned as a {@link Flux} for streaming.
     *
     * @param status  HTTP status code from upstream
     * @param headers filtered response headers
     * @param body    buffered response body (null for SSE)
     * @param sseBody streaming body flux (null for non-SSE)
     */
    public record UpstreamResult(int status, HttpHeaders headers, byte[] body, Flux<DataBuffer> sseBody) {}
}
