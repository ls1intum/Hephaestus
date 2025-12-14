package de.tum.in.www1.hephaestus.proxy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

@RestController
@Hidden
@RequestMapping("/mentor")
public class MentorProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        HttpHeaders.CONNECTION,
    "Keep-Alive",
        HttpHeaders.PROXY_AUTHENTICATE,
        HttpHeaders.PROXY_AUTHORIZATION,
        HttpHeaders.TE,
        HttpHeaders.TRAILER,
        HttpHeaders.TRANSFER_ENCODING,
        HttpHeaders.UPGRADE
    );

    private final WebClient mentorWebClient;
    private final String intelligenceServiceBaseUrl;

    public MentorProxyController(
        WebClient mentorWebClient,
        @Value("${hephaestus.intelligence-service.url}") String intelligenceServiceBaseUrl
    ) {
        this.mentorWebClient = mentorWebClient;
        this.intelligenceServiceBaseUrl = intelligenceServiceBaseUrl;
    }

    /**
     * Smart proxy that detects SSE (text/event-stream) responses and streams them properly.
     * For non-streaming responses, returns the full body.
     */
    @RequestMapping("/**")
    public ResponseEntity<?> proxy(
        HttpServletRequest request,
        @RequestHeader HttpHeaders incomingHeaders,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) byte[] body
    ) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Build downstream URI: base + original path (preserve '/mentor' prefix) + query
        String rawPath = request.getRequestURI();
        String query = request.getQueryString();
        String target = intelligenceServiceBaseUrl + rawPath + (query != null ? ("?" + query) : "");

        // Copy headers, remove hop-by-hop and Host; preserve content-type if present
        HttpHeaders outHeaders = new HttpHeaders();
        for (Map.Entry<String, List<String>> e : incomingHeaders.entrySet()) {
            if (!HOP_BY_HOP_HEADERS.contains(e.getKey())) {
                outHeaders.put(e.getKey(), e.getValue());
            }
        }
        outHeaders.remove(HttpHeaders.HOST);
        // Avoid double-encoding
        outHeaders.set(HttpHeaders.ACCEPT_ENCODING, "");

        if (jwt != null) {
            outHeaders.setBearerAuth(jwt.getTokenValue());
        }

        WebClient.RequestBodySpec reqSpec = mentorWebClient
            .method(method)
            .uri(URI.create(target))
            .headers(h -> {
                h.clear();
                h.addAll(outHeaders);
            });

        byte[] safeBody = body != null ? body : new byte[0];

        // Check if the upstream returns SSE (text/event-stream) - if so, stream it properly
        return reqSpec
            .bodyValue(safeBody)
            .exchangeToMono(clientResponse -> {
                HttpHeaders respHeaders = new HttpHeaders();
                clientResponse.headers().asHttpHeaders().forEach((k, v) -> {
                    if (!HOP_BY_HOP_HEADERS.contains(k)) {
                        respHeaders.put(k, v);
                    }
                });

                MediaType contentType = clientResponse.headers().contentType().orElse(null);
                boolean isEventStream = contentType != null && 
                    contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM);

                if (isEventStream) {
                    // Stream SSE responses using Flux<DataBuffer>
                    Flux<DataBuffer> dataFlux = clientResponse.bodyToFlux(DataBuffer.class);
                    
                    // Return streaming response
                    StreamingResponseBody streamBody = outputStream -> {
                        dataFlux.toIterable().forEach(buffer -> {
                            try {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                outputStream.write(bytes);
                                outputStream.flush();
                            } catch (Exception e) {
                                // Ignore write errors (client disconnected)
                            }
                        });
                    };
                    
                    return reactor.core.publisher.Mono.just(
                        ResponseEntity.status(clientResponse.statusCode())
                            .headers(respHeaders)
                            .body(streamBody)
                    );
                } else {
                    // Non-streaming: buffer the entire response
                    return clientResponse
                        .bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(bytes -> ResponseEntity.status(clientResponse.statusCode())
                            .headers(respHeaders)
                            .body(bytes));
                }
            })
            .block();
    }
}
