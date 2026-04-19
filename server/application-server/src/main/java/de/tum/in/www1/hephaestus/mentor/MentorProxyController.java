package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.config.IntelligenceServiceProperties;
import de.tum.in.www1.hephaestus.core.proxy.ProxyStreamingUtils;
import de.tum.in.www1.hephaestus.gitprovider.user.AuthenticatedUserService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.client.WebClient;

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

    private final WebClient mentorWebClient;
    private final String intelligenceServiceBaseUrl;
    private final AuthenticatedUserService authenticatedUserService;

    public MentorProxyController(
        WebClient mentorWebClient,
        IntelligenceServiceProperties intelligenceServiceProperties,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.mentorWebClient = mentorWebClient;
        this.intelligenceServiceBaseUrl = intelligenceServiceProperties.url();
        this.authenticatedUserService = authenticatedUserService;
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
        User currentUser = authenticatedUserService.findPrimaryUser().orElse(null);
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

        ProxyStreamingUtils.UpstreamResult upstream;
        try {
            var baseSpec = mentorWebClient
                .method(method)
                .uri(URI.create(target))
                .headers(h -> {
                    h.clear();
                    h.addAll(outHeaders);
                });

            // Only attach body for methods that typically carry one (avoids Content-Length: 0 on GET)
            WebClient.RequestHeadersSpec<?> readySpec = (body != null && body.length > 0)
                ? baseSpec.bodyValue(body)
                : baseSpec;

            upstream = readySpec.exchangeToMono(ProxyStreamingUtils::consumeResponse).block(Duration.ofSeconds(310));
        } catch (Exception e) {
            log.warn("Intelligence service unreachable: {}", e.getMessage());
            return ResponseEntity.status(502).body("Upstream service unreachable");
        }

        if (upstream == null) {
            return ResponseEntity.status(502).body("Upstream service unavailable");
        }

        if (upstream.sseBody() != null) {
            ProxyStreamingUtils.streamSseToResponse(
                upstream.sseBody(),
                upstream.headers(),
                response,
                upstream.status()
            );
            return null; // Response already committed
        } else {
            return ResponseEntity.status(upstream.status()).headers(upstream.headers()).body(upstream.body());
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
        HttpHeaders outHeaders = ProxyStreamingUtils.filterHopByHopHeaders(incomingHeaders);
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
}
