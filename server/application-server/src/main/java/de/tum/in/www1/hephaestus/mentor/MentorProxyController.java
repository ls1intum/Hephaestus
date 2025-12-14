package de.tum.in.www1.hephaestus.mentor;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import reactor.core.publisher.Flux;

/**
 * Transparent proxy for mentor endpoints to the intelligence service.
 *
 * This controller is hidden from OpenAPI generation - the schemas and paths
 * are imported directly from the intelligence service's OpenAPI spec via
 * {@link de.tum.in.www1.hephaestus.OpenAPIConfiguration}.
 */
@RestController
@Hidden
@RequestMapping("/workspaces/{workspaceSlug}/mentor")
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
     * Catch-all proxy that forwards all /mentor/** requests to the intelligence service.
     * Automatically handles SSE streaming for chat responses.
     */
    @RequestMapping("/**")
    public ResponseEntity<?> proxy(
        HttpServletRequest request,
        @RequestHeader HttpHeaders incomingHeaders,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) byte[] body
    ) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Extract the mentor-relative path (remove /workspaces/{slug} prefix)
        String fullPath = request.getRequestURI();
        String mentorPath = fullPath.replaceFirst("/workspaces/[^/]+", "");

        String query = request.getQueryString();
        String target = intelligenceServiceBaseUrl + mentorPath + (query != null ? ("?" + query) : "");

        HttpHeaders outHeaders = prepareOutgoingHeaders(incomingHeaders, jwt);
        byte[] safeBody = body != null ? body : new byte[0];

        return mentorWebClient
            .method(method)
            .uri(URI.create(target))
            .headers(h -> {
                h.clear();
                h.addAll(outHeaders);
            })
            .bodyValue(safeBody)
            .exchangeToMono(clientResponse -> {
                HttpHeaders respHeaders = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
                MediaType contentType = clientResponse.headers().contentType().orElse(null);
                boolean isEventStream =
                    contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM);

                if (isEventStream) {
                    // Stream SSE responses
                    Flux<DataBuffer> dataFlux = clientResponse.bodyToFlux(DataBuffer.class);
                    StreamingResponseBody streamBody = outputStream -> {
                        dataFlux
                            .toIterable()
                            .forEach(buffer -> {
                                try {
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    outputStream.write(bytes);
                                    outputStream.flush();
                                } catch (Exception e) {}
                            });
                    };
                    return reactor.core.publisher.Mono.just(
                        ResponseEntity.status(clientResponse.statusCode()).headers(respHeaders).body(streamBody)
                    );
                } else {
                    // Buffer non-streaming responses
                    return clientResponse
                        .bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(bytes ->
                            ResponseEntity.status(clientResponse.statusCode()).headers(respHeaders).body(bytes)
                        );
                }
            })
            .block();
    }

    private HttpHeaders prepareOutgoingHeaders(HttpHeaders incomingHeaders, Jwt jwt) {
        HttpHeaders outHeaders = new HttpHeaders();
        for (Map.Entry<String, List<String>> e : incomingHeaders.entrySet()) {
            if (!HOP_BY_HOP_HEADERS.contains(e.getKey())) {
                outHeaders.put(e.getKey(), e.getValue());
            }
        }
        outHeaders.remove(HttpHeaders.HOST);
        outHeaders.set(HttpHeaders.ACCEPT_ENCODING, "");
        if (jwt != null) {
            outHeaders.setBearerAuth(jwt.getTokenValue());
        }
        return outHeaders;
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders headers) {
        HttpHeaders respHeaders = new HttpHeaders();
        headers.forEach((k, v) -> {
            if (!HOP_BY_HOP_HEADERS.contains(k)) {
                respHeaders.put(k, v);
            }
        });
        return respHeaders;
    }
}
