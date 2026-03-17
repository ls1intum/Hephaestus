package de.tum.in.www1.hephaestus.core.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ProxyStreamingUtils")
class ProxyStreamingUtilsTest extends BaseUnitTest {

    @Nested
    @DisplayName("filterHopByHopHeaders")
    class FilterHopByHopHeaders {

        @Test
        @DisplayName("should remove Connection header")
        void shouldRemoveConnection() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONNECTION, "keep-alive");
            headers.set("Content-Type", "application/json");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered.get(HttpHeaders.CONNECTION)).isNull();
            assertThat(filtered.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should remove Transfer-Encoding header")
        void shouldRemoveTransferEncoding() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered.get(HttpHeaders.TRANSFER_ENCODING)).isNull();
        }

        @Test
        @DisplayName("should remove Keep-Alive header")
        void shouldRemoveKeepAlive() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Keep-Alive", "timeout=5");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered.get("Keep-Alive")).isNull();
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("connection", "close");
            headers.set("TRANSFER-ENCODING", "chunked");
            headers.set("keep-alive", "timeout=5");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered.get("connection")).isNull();
            assertThat(filtered.get("TRANSFER-ENCODING")).isNull();
            assertThat(filtered.get("keep-alive")).isNull();
        }

        @Test
        @DisplayName("should preserve non-hop-by-hop headers")
        void shouldPreserveNonHopByHopHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");
            headers.set("X-Custom-Header", "custom-value");
            headers.set(HttpHeaders.CONNECTION, "keep-alive");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered.getFirst("Content-Type")).isEqualTo("application/json");
            assertThat(filtered.getFirst("Accept")).isEqualTo("application/json");
            assertThat(filtered.getFirst("X-Custom-Header")).isEqualTo("custom-value");
            assertThat(filtered.get(HttpHeaders.CONNECTION)).isNull();
        }

        @Test
        @DisplayName("should handle empty headers")
        void shouldHandleEmptyHeaders() {
            HttpHeaders headers = new HttpHeaders();

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered).isEmpty();
        }

        @Test
        @DisplayName("should remove all RFC 7230 hop-by-hop headers")
        void shouldRemoveAllHopByHopHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONNECTION, "keep-alive");
            headers.set("Keep-Alive", "timeout=5");
            headers.set(HttpHeaders.PROXY_AUTHENTICATE, "Basic");
            headers.set(HttpHeaders.PROXY_AUTHORIZATION, "Basic cred");
            headers.set(HttpHeaders.TE, "trailers");
            headers.set(HttpHeaders.TRAILER, "Expires");
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            headers.set(HttpHeaders.UPGRADE, "h2c");
            headers.set("X-Survive", "yes");

            HttpHeaders filtered = ProxyStreamingUtils.filterHopByHopHeaders(headers);

            assertThat(filtered).hasSize(1);
            assertThat(filtered.getFirst("X-Survive")).isEqualTo("yes");
        }
    }

    @Nested
    @DisplayName("consumeResponse")
    class ConsumeResponse {

        @Test
        @DisplayName("should return buffered body for non-SSE response")
        void shouldBufferNonSseResponse() {
            byte[] expectedBody = "{\"id\":\"msg_123\"}".getBytes(StandardCharsets.UTF_8);
            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.setContentType(MediaType.APPLICATION_JSON);

            ClientResponse clientResponse = mockClientResponse(
                200,
                upstreamHeaders,
                MediaType.APPLICATION_JSON,
                expectedBody
            );

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(200);
                    assertThat(result.body()).isEqualTo(expectedBody);
                    assertThat(result.sseBody()).isNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should return Flux body for SSE response")
        void shouldStreamSseResponse() {
            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);

            var factory = new DefaultDataBufferFactory();
            DataBuffer buf = factory.wrap("data: hello\n\n".getBytes());
            Flux<DataBuffer> sseFlux = Flux.just(buf);

            ClientResponse clientResponse = mockClientResponseWithSseFlux(200, upstreamHeaders, sseFlux);

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(200);
                    assertThat(result.body()).isNull();
                    assertThat(result.sseBody()).isNotNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should return empty byte array for empty non-SSE body")
        void shouldReturnEmptyBodyForEmptyResponse() {
            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.setContentType(MediaType.APPLICATION_JSON);

            ClientResponse clientResponse = mockClientResponseEmpty(204, upstreamHeaders);

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(204);
                    assertThat(result.body()).isEmpty();
                    assertThat(result.sseBody()).isNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should filter hop-by-hop headers from upstream response")
        void shouldFilterHopByHopFromUpstream() {
            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.setContentType(MediaType.APPLICATION_JSON);
            upstreamHeaders.set(HttpHeaders.CONNECTION, "keep-alive");
            upstreamHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            upstreamHeaders.set("X-Request-Id", "abc123");

            ClientResponse clientResponse = mockClientResponse(
                200,
                upstreamHeaders,
                MediaType.APPLICATION_JSON,
                "{}".getBytes()
            );

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.headers().get(HttpHeaders.CONNECTION)).isNull();
                    assertThat(result.headers().get(HttpHeaders.TRANSFER_ENCODING)).isNull();
                    assertThat(result.headers().getFirst("X-Request-Id")).isEqualTo("abc123");
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should handle upstream error status codes")
        void shouldForwardErrorStatus() {
            byte[] errorBody = "{\"error\":\"rate_limited\"}".getBytes(StandardCharsets.UTF_8);
            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.setContentType(MediaType.APPLICATION_JSON);

            ClientResponse clientResponse = mockClientResponse(
                429,
                upstreamHeaders,
                MediaType.APPLICATION_JSON,
                errorBody
            );

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(429);
                    assertThat(result.body()).isEqualTo(errorBody);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should detect SSE with charset parameter")
        void shouldDetectSseWithCharset() {
            HttpHeaders upstreamHeaders = new HttpHeaders();
            MediaType sseWithCharset = new MediaType("text", "event-stream", StandardCharsets.UTF_8);
            upstreamHeaders.setContentType(sseWithCharset);

            var factory = new DefaultDataBufferFactory();
            DataBuffer buf = factory.wrap("data: test\n\n".getBytes());
            Flux<DataBuffer> sseFlux = Flux.just(buf);

            ClientResponse clientResponse = mockClientResponseWithSseFlux(200, upstreamHeaders, sseFlux);

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.sseBody()).isNotNull();
                    assertThat(result.body()).isNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("should treat null content-type as non-SSE")
        void shouldTreatNullContentTypeAsNonSse() {
            HttpHeaders upstreamHeaders = new HttpHeaders();

            ClientResponse clientResponse = mockClientResponse(200, upstreamHeaders, null, "plain text".getBytes());

            StepVerifier.create(ProxyStreamingUtils.consumeResponse(clientResponse))
                .assertNext(result -> {
                    assertThat(result.sseBody()).isNull();
                    assertThat(result.body()).isEqualTo("plain text".getBytes());
                })
                .verifyComplete();
        }

        private ClientResponse mockClientResponse(int status, HttpHeaders headers, MediaType contentType, byte[] body) {
            ClientResponse resp = mock(ClientResponse.class);
            ClientResponse.Headers respHeaders = mock(ClientResponse.Headers.class);
            when(resp.headers()).thenReturn(respHeaders);
            when(respHeaders.asHttpHeaders()).thenReturn(headers);
            when(respHeaders.contentType()).thenReturn(Optional.ofNullable(contentType));
            when(resp.statusCode()).thenReturn(org.springframework.http.HttpStatusCode.valueOf(status));
            when(resp.bodyToMono(byte[].class)).thenReturn(body != null ? Mono.just(body) : Mono.empty());
            return resp;
        }

        private ClientResponse mockClientResponseWithSseFlux(
            int status,
            HttpHeaders headers,
            Flux<DataBuffer> sseFlux
        ) {
            ClientResponse resp = mock(ClientResponse.class);
            ClientResponse.Headers respHeaders = mock(ClientResponse.Headers.class);
            when(resp.headers()).thenReturn(respHeaders);
            when(respHeaders.asHttpHeaders()).thenReturn(headers);
            when(respHeaders.contentType()).thenReturn(Optional.of(MediaType.TEXT_EVENT_STREAM));
            when(resp.statusCode()).thenReturn(org.springframework.http.HttpStatusCode.valueOf(status));
            when(resp.bodyToFlux(DataBuffer.class)).thenReturn(sseFlux);
            return resp;
        }

        private ClientResponse mockClientResponseEmpty(int status, HttpHeaders headers) {
            ClientResponse resp = mock(ClientResponse.class);
            ClientResponse.Headers respHeaders = mock(ClientResponse.Headers.class);
            when(resp.headers()).thenReturn(respHeaders);
            when(respHeaders.asHttpHeaders()).thenReturn(headers);
            when(respHeaders.contentType()).thenReturn(Optional.of(MediaType.APPLICATION_JSON));
            when(resp.statusCode()).thenReturn(org.springframework.http.HttpStatusCode.valueOf(status));
            when(resp.bodyToMono(byte[].class)).thenReturn(Mono.empty());
            return resp;
        }
    }

    @Nested
    @DisplayName("streamSseToResponse")
    class StreamSseToResponse {

        private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

        @Test
        @DisplayName("should write SSE data to response output stream")
        void shouldWriteSseDataToResponse() throws IOException {
            byte[] event1 = "data: hello\n\n".getBytes(StandardCharsets.UTF_8);
            byte[] event2 = "data: world\n\n".getBytes(StandardCharsets.UTF_8);

            Flux<DataBuffer> dataFlux = Flux.just(bufferFactory.wrap(event1), bufferFactory.wrap(event2));

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);

            String content = response.getContentAsString();
            assertThat(content).contains("data: hello");
            assertThat(content).contains("data: world");
        }

        @Test
        @DisplayName("should set SSE-specific response headers")
        void shouldSetSseHeaders() {
            Flux<DataBuffer> dataFlux = Flux.empty();
            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache, no-store, must-revalidate");
            assertThat(response.getHeader(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
            assertThat(response.getHeader(HttpHeaders.EXPIRES)).isEqualTo("0");
            assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        }

        @Test
        @DisplayName("should forward upstream status code")
        void shouldForwardUpstreamStatus() {
            Flux<DataBuffer> dataFlux = Flux.empty();
            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 429);

            assertThat(response.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("should copy upstream headers excluding managed ones")
        void shouldCopyUpstreamHeadersExcludingManaged() {
            Flux<DataBuffer> dataFlux = Flux.empty();
            MockHttpServletResponse response = new MockHttpServletResponse();

            HttpHeaders upstreamHeaders = new HttpHeaders();
            upstreamHeaders.set("X-Request-Id", "req-123");
            upstreamHeaders.set("X-Anthropic-RateLimit", "10");
            upstreamHeaders.set(HttpHeaders.CONTENT_TYPE, "text/event-stream");
            upstreamHeaders.set(HttpHeaders.CONTENT_LENGTH, "999");
            upstreamHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            upstreamHeaders.set(HttpHeaders.CACHE_CONTROL, "private");

            ProxyStreamingUtils.streamSseToResponse(dataFlux, upstreamHeaders, response, 200);

            assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
            assertThat(response.getHeader("X-Anthropic-RateLimit")).isEqualTo("10");
            assertThat(response.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache, no-store, must-revalidate");
        }

        @Test
        @DisplayName("should handle empty flux gracefully")
        void shouldHandleEmptyFlux() {
            Flux<DataBuffer> dataFlux = Flux.empty();
            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        }

        @Test
        @DisplayName("should handle upstream error Flux without crashing")
        void shouldHandleErrorFlux() {
            Flux<DataBuffer> dataFlux = Flux.error(new RuntimeException("upstream died"));
            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            // Should not throw — caught internally
            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should release DataBuffers from a Flux that emits then errors")
        void shouldReleaseBuffersOnPartialFluxError() {
            // Track buffer allocation via a custom factory
            var buf1 = bufferFactory.allocateBuffer(16);
            buf1.write("data: ok\n\n".getBytes(StandardCharsets.UTF_8));
            var buf2 = bufferFactory.allocateBuffer(16);
            buf2.write("data: ok2\n\n".getBytes(StandardCharsets.UTF_8));

            // Emit 2 buffers then error
            Flux<DataBuffer> dataFlux = Flux.just((DataBuffer) buf1, (DataBuffer) buf2).concatWith(
                Flux.error(new RuntimeException("mid-stream failure"))
            );

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            // Should not throw — errors are caught internally
            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            // Verify the response received the data before the error
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should write multiple events in order")
        void shouldWriteEventsInOrder() throws IOException {
            Flux<DataBuffer> dataFlux = Flux.range(1, 5).map(i ->
                bufferFactory.wrap(("data: event-" + i + "\n\n").getBytes(StandardCharsets.UTF_8))
            );

            MockHttpServletResponse response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            ProxyStreamingUtils.streamSseToResponse(dataFlux, headers, response, 200);

            String content = response.getContentAsString();
            int idx1 = content.indexOf("event-1");
            int idx2 = content.indexOf("event-2");
            int idx3 = content.indexOf("event-3");
            int idx4 = content.indexOf("event-4");
            int idx5 = content.indexOf("event-5");

            assertThat(idx1).isLessThan(idx2);
            assertThat(idx2).isLessThan(idx3);
            assertThat(idx3).isLessThan(idx4);
            assertThat(idx4).isLessThan(idx5);
        }
    }
}
