package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.config.IntelligenceServiceProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Transparent proxy for mentor endpoints to the intelligence service.
 *
 * <p>This controller is hidden from OpenAPI generation - the schemas and paths
 * are imported directly from the intelligence service's OpenAPI spec via
 * {@link de.tum.in.www1.hephaestus.OpenAPIConfiguration}.</p>
 *
 * <p>Uses {@link WorkspaceScopedController} to ensure proper workspace
 * authorization and context injection.</p>
 *
 * <p><b>SSE Streaming:</b> For Server-Sent Events responses, this controller writes
 * directly to the {@link HttpServletResponse} output stream. This bypasses Spring MVC's
 * message converters which do not properly handle {@code StreamingResponseBody} with
 * preset {@code Content-Type: text/event-stream}. Writing directly ensures proper
 * real-time streaming to the client.</p>
 */
@WorkspaceScopedController
@Hidden
@RequestMapping("/mentor")
@PreAuthorize("isAuthenticated()")
public class MentorProxyController {

    private static final Logger log = LoggerFactory.getLogger(MentorProxyController.class);

    /** Header used to pass workspace ID to the intelligence service. */
    public static final String WORKSPACE_ID_HEADER = "X-Workspace-Id";
    /** Header used to pass workspace slug to the intelligence service. */
    public static final String WORKSPACE_SLUG_HEADER = "X-Workspace-Slug";
    /** Header used to pass user ID to the intelligence service. */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** Header used to pass user login to the intelligence service. */
    public static final String USER_LOGIN_HEADER = "X-User-Login";
    /** Header used to pass user's first name to the intelligence service. */
    public static final String USER_FIRST_NAME_HEADER = "X-User-First-Name";

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
    private final UserRepository userRepository;

    public MentorProxyController(
        WebClient mentorWebClient,
        IntelligenceServiceProperties intelligenceServiceProperties,
        UserRepository userRepository
    ) {
        this.mentorWebClient = mentorWebClient;
        this.intelligenceServiceBaseUrl = intelligenceServiceProperties.url();
        this.userRepository = userRepository;
    }

    /**
     * Catch-all proxy that forwards all /mentor/** requests to the intelligence service.
     *
     * <p>For SSE (text/event-stream) responses, writes directly to HttpServletResponse
     * and returns null, bypassing Spring's message converters. For other responses,
     * returns a normal ResponseEntity.</p>
     *
     * @param workspaceContext The resolved workspace context from the request path
     * @param request The incoming HTTP request
     * @param response The HTTP response (used for SSE streaming)
     * @param incomingHeaders Headers from the incoming request
     * @param jwt The authenticated user's JWT token
     * @param body The request body (if any)
     * @return ResponseEntity for non-SSE responses, null for SSE (already written to response)
     */
    @RequestMapping("/**")
    public ResponseEntity<?> proxy(
        WorkspaceContext workspaceContext,
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader HttpHeaders incomingHeaders,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) byte[] body
    ) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Extract the mentor-relative path (workspace prefix is already handled)
        String fullPath = request.getRequestURI();
        String mentorPath = fullPath.replaceFirst("/workspaces/[^/]+", "");

        String query = request.getQueryString();
        String target = intelligenceServiceBaseUrl + mentorPath + (query != null ? ("?" + query) : "");

        // Look up the current user for passing user context to intelligence service
        User currentUser = userRepository.getCurrentUser().orElse(null);
        if (currentUser == null) {
            var login = SecurityUtils.getCurrentUserLogin().orElse("unknown");
            log.warn(
                "User '{}' authenticated but not found in git_user table - documents will not be persisted",
                login
            );
        } else {
            log.debug("Found user '{}' (id={}) for mentor request", currentUser.getLogin(), currentUser.getId());
        }
        HttpHeaders outHeaders = prepareOutgoingHeaders(incomingHeaders, jwt, workspaceContext, currentUser);
        byte[] safeBody = body != null ? body : new byte[0];

        // First, make the request and check if it's SSE
        var clientResponse = mentorWebClient
            .method(method)
            .uri(URI.create(target))
            .headers(h -> {
                h.clear();
                h.addAll(outHeaders);
            })
            .bodyValue(safeBody)
            .exchangeToMono(clientResp -> Mono.just(clientResp))
            .block();

        if (clientResponse == null) {
            return ResponseEntity.status(502).body("Upstream service unavailable");
        }

        HttpHeaders respHeaders = filterResponseHeaders(clientResponse.headers().asHttpHeaders());
        MediaType contentType = clientResponse.headers().contentType().orElse(null);
        boolean isEventStream = contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM);

        if (isEventStream) {
            // For SSE, stream directly to HttpServletResponse on the current servlet thread
            streamSseToResponse(
                clientResponse.bodyToFlux(DataBuffer.class),
                respHeaders,
                response,
                clientResponse.statusCode().value()
            );
            return null; // Response already committed
        } else {
            // Buffer non-streaming responses and return as ResponseEntity
            byte[] bytes = clientResponse.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).block();
            return ResponseEntity.status(clientResponse.statusCode()).headers(respHeaders).body(bytes);
        }
    }

    /**
     * Streams SSE data directly to the HttpServletResponse output stream.
     *
     * <p>This method bypasses Spring MVC's message converters which do not properly handle
     * SSE streaming with preset Content-Type headers. By writing directly to the servlet
     * response, we ensure proper real-time streaming behavior.</p>
     *
     * <p>This method blocks until the stream completes or the client disconnects.</p>
     *
     * @param dataFlux The reactive stream of data buffers from the upstream service
     * @param respHeaders Headers to copy to the response
     * @param response The servlet response to write to
     * @param statusCode The HTTP status code from the upstream response
     */
    private void streamSseToResponse(
        Flux<DataBuffer> dataFlux,
        HttpHeaders respHeaders,
        HttpServletResponse response,
        int statusCode
    ) {
        try {
            // Set response status and headers for SSE
            response.setStatus(statusCode);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding("UTF-8");

            // Disable caching for SSE
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");

            // Copy other headers (excluding ones we set ourselves)
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

            // Flush headers immediately to start streaming
            response.flushBuffer();

            OutputStream outputStream = response.getOutputStream();

            // Block and stream each buffer to the client - this runs on servlet thread which allows blocking
            dataFlux
                .doOnNext(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        outputStream.write(bytes);
                        outputStream.flush();
                    } catch (IOException e) {
                        log.debug("Client disconnected during SSE streaming: {}", e.getMessage());
                        throw new StreamingException("Client disconnected", e);
                    }
                })
                .doOnError(e -> log.debug("SSE stream error: {}", e.getMessage()))
                .doOnComplete(() -> {
                    try {
                        outputStream.flush();
                    } catch (IOException e) {
                        log.debug("Error flushing final SSE output: {}", e.getMessage());
                    }
                })
                .blockLast(); // Safe to block here - we're on a servlet thread
        } catch (IOException e) {
            log.debug("SSE streaming initialization failed: {}", e.getMessage());
        } catch (StreamingException e) {
            // Client disconnected - this is expected behavior, not an error
            log.debug("SSE streaming terminated: {}", e.getMessage());
        }
    }

    /**
     * Prepares outgoing headers for the upstream request.
     * Removes hop-by-hop headers and adds workspace context, user context, and auth headers.
     */
    private HttpHeaders prepareOutgoingHeaders(
        HttpHeaders incomingHeaders,
        Jwt jwt,
        WorkspaceContext workspaceContext,
        User currentUser
    ) {
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
        if (workspaceContext != null) {
            if (workspaceContext.id() != null) {
                outHeaders.set(WORKSPACE_ID_HEADER, String.valueOf(workspaceContext.id()));
            }
            if (workspaceContext.slug() != null) {
                outHeaders.set(WORKSPACE_SLUG_HEADER, workspaceContext.slug());
            }
        }
        if (currentUser != null) {
            outHeaders.set(USER_ID_HEADER, String.valueOf(currentUser.getId()));
            if (currentUser.getLogin() != null) {
                outHeaders.set(USER_LOGIN_HEADER, currentUser.getLogin());
            }
        }
        // Get first name from JWT's given_name claim (Keycloak standard OIDC claim)
        SecurityUtils.getGivenName(jwt).ifPresent(name -> outHeaders.set(USER_FIRST_NAME_HEADER, name));
        return outHeaders;
    }

    /**
     * Filters response headers, removing hop-by-hop headers that shouldn't be forwarded.
     */
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
