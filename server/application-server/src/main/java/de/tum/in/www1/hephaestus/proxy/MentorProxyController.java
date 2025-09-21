package de.tum.in.www1.hephaestus.proxy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.servlet.http.HttpServletRequest;

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

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(
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

        return reqSpec
            .bodyValue(safeBody)
            .exchangeToMono(clientResponse -> clientResponse
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    HttpHeaders resp = new HttpHeaders();
                    clientResponse.headers().asHttpHeaders().forEach((k, v) -> {
                        if (!HOP_BY_HOP_HEADERS.contains(k)) {
                            resp.put(k, v);
                        }
                    });
                    return ResponseEntity.status(clientResponse.statusCode()).headers(resp).body(bytes);
                })
            )
            .block();
    }
}
