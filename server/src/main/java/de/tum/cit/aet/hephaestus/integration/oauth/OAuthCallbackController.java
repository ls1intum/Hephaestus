package de.tum.cit.aet.hephaestus.integration.oauth;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy.ConnectFinalization;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.spi.OAuthStateService.StateBinding;
import de.tum.cit.aet.hephaestus.integration.webhook.IntegrationKindRouting;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the vendor-side OAuth redirect at {@code /oauth/callback/{kind}} and finalises
 * the Connection by handing off to the per-kind {@link ConnectionStrategy}.
 *
 * <h2>Security model</h2>
 * This endpoint is intentionally unauthenticated ({@code @PreAuthorize("permitAll()")}).
 * The vendor (Slack, Outline, GitHub App settings UI, …) redirects the user's browser
 * back here with no Hephaestus session — there is no opportunity to attach a JWT.
 * Authentication is enforced via the HMAC-signed {@code state} parameter minted by
 * {@link OAuthStateService} at the start of the flow:
 * <ul>
 *   <li>the state binds {@code (workspaceId, kind, issuedAt, actorRef)};</li>
 *   <li>the HMAC prevents forgery and the TTL prevents long-tail replays;</li>
 *   <li>the {@code kind} embedded in the state MUST match the {@code {kind}} path
 *       segment — otherwise a state issued for kind A could be replayed against the
 *       callback path for kind B (the strategies' {@code finalizeConnect} contract
 *       does not include a kind self-check).</li>
 * </ul>
 *
 * <h2>Outcome encoding</h2>
 * The browser is the audience here — humans, not programs. Success and vendor-side
 * cancellations therefore return HTTP 302 redirects to the configured Hephaestus UI
 * pages. Programmatic / framework errors (malformed state, unknown kind, strategy
 * failure with no UI to bounce to) return HTTP 4xx with a small JSON body so the
 * problem surfaces in browser dev tools and proxy logs.
 *
 * <h2>Architecture posture</h2>
 * Thin HTTP adapter: all repository access lives in {@link OAuthCallbackService} so the
 * controller stays under the {@code controllersDoNotAccessRepositories} +
 * {@code controllersAreThin} (max 5 constructor params) ceilings. The two
 * redirect-target strings are bundled in {@link OAuthCallbackProperties} for the same
 * reason — every {@code @Value} string counts as a separate constructor param.
 *
 * <h2>Workspace context (architecture rule exemption)</h2>
 * {@code MultiTenancyArchitectureTest.dataEndpointsReceiveWorkspaceContext} expects a
 * {@code workspaceId} path variable or workspace security annotation on every controller
 * method. This controller is exempt — workspace identity is derived from the verified
 * state binding, NEVER from the request — and the rule has an explicit allow-list entry
 * matching the {@code OAuthCallbackController} class simple name.
 */
@RestController
@RequestMapping("/oauth/callback")
public class OAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final IntegrationKindRouting kindRouting;
    private final OAuthStateService oauthStateService;
    private final OAuthCallbackService callbackService;
    private final Map<IntegrationKind, ConnectionStrategy> strategies;
    private final OAuthCallbackProperties properties;

    public OAuthCallbackController(
        IntegrationKindRouting kindRouting,
        OAuthStateService oauthStateService,
        OAuthCallbackService callbackService,
        List<ConnectionStrategy> strategyBeans,
        OAuthCallbackProperties properties
    ) {
        this.kindRouting = kindRouting;
        this.oauthStateService = oauthStateService;
        this.callbackService = callbackService;
        this.strategies = strategyBeans.stream().collect(Collectors.toUnmodifiableMap(
            ConnectionStrategy::kind,
            s -> s,
            (a, b) -> {
                throw new IllegalStateException(
                    "Duplicate ConnectionStrategy for kind=" + a.kind() + ": "
                        + a.getClass() + " vs " + b.getClass()
                );
            }
        ));
        this.properties = properties;
    }

    /**
     * Vendor-style GET callback: Slack, Outline, GitHub App install all bounce here.
     *
     * <p>The {@code error} param is checked BEFORE state — Slack/Outline send
     * {@code error=access_denied} with no {@code code} when the user clicks "cancel"
     * on the consent page, and we surface that as 400 rather than blowing up on the
     * missing state.
     */
    @GetMapping("/{kind}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> callbackGet(
        @PathVariable String kind,
        @RequestParam(value = "code", required = false) @Nullable String code,
        @RequestParam(value = "state", required = false) @Nullable String state,
        @RequestParam(value = "error", required = false) @Nullable String error,
        @RequestParam(value = "error_description", required = false) @Nullable String errorDescription,
        @RequestParam Map<String, String> allParams
    ) {
        return handleCallback(kind, code, state, error, errorDescription, allParams);
    }

    /**
     * POST variant for vendors that send form-encoded callbacks. Slack's OAuth v2
     * exchange is GET-based today, but webhook-style integrations (Outline custom
     * apps, possible future Slack flows) post the code body — accepting both keeps
     * the controller robust to vendor-side changes without code churn.
     */
    @PostMapping("/{kind}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> callbackPost(
        @PathVariable String kind,
        @RequestParam(value = "code", required = false) @Nullable String code,
        @RequestParam(value = "state", required = false) @Nullable String state,
        @RequestParam(value = "error", required = false) @Nullable String error,
        @RequestParam(value = "error_description", required = false) @Nullable String errorDescription,
        @RequestParam Map<String, String> allParams
    ) {
        return handleCallback(kind, code, state, error, errorDescription, allParams);
    }

    /**
     * Shared core for GET + POST callbacks. Pulled out so the two HTTP entry methods
     * stay thin and the test surface is one method, not two.
     */
    ResponseEntity<?> handleCallback(
        String kindPathSegment,
        @Nullable String code,
        @Nullable String state,
        @Nullable String vendorError,
        @Nullable String vendorErrorDescription,
        Map<String, String> allParams
    ) {
        // ── 1. Resolve kind via allow-list (NEVER IntegrationKind.valueOf on raw input) ──
        Optional<IntegrationKind> kindOpt = kindRouting.resolve(kindPathSegment);
        if (kindOpt.isEmpty()) {
            log.info("OAuth callback for unknown kind path segment: {}", sanitize(kindPathSegment));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                errorBody(kindPathSegment, "unknown_kind",
                    "No integration registered for path segment: " + sanitize(kindPathSegment))
            );
        }
        IntegrationKind kind = kindOpt.get();

        // ── 2. Vendor-side cancellation: surface the error without consuming state ──
        // The state may still be present and valid; we leave it usable (it'll expire
        // naturally via TTL) rather than racing the user who might immediately retry.
        if (vendorError != null && !vendorError.isBlank()) {
            log.info("OAuth callback for kind={} returned vendor error={} description={}",
                kind, sanitize(vendorError), sanitize(vendorErrorDescription));
            // TODO(#1198 follow-up): if the state carried a redirectAfter URI, bounce
            // there with ?status=error&reason=<vendorError>; for now return 400 JSON.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), vendorError, vendorErrorDescription)
            );
        }

        // ── 3. Verify state (HMAC + TTL) ─────────────────────────────────────
        if (state == null || state.isBlank()) {
            log.info("OAuth callback for kind={} missing state parameter", kind);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), "missing_state", "state parameter is required")
            );
        }
        StateBinding binding;
        try {
            binding = oauthStateService.consume(state);
        } catch (IllegalArgumentException e) {
            // HMAC mismatch / expired / malformed — log without the raw state to keep
            // attacker-supplied junk out of logs.
            log.warn("OAuth callback for kind={} rejected state: {}", kind, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), "invalid_state", e.getMessage())
            );
        }

        // ── 4. State-kind must match path-kind (defends against cross-kind replay) ──
        if (binding.kind() != kind) {
            log.warn("OAuth state-kind mismatch: path={} stateKind={} workspace={}",
                kind, binding.kind(), binding.workspaceId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), "kind_mismatch",
                    "State issued for kind=" + binding.kind() + " replayed against callback path /oauth/callback/" + kindPathSegment)
            );
        }

        // ── 5. Strategy lookup ────────────────────────────────────────────────
        ConnectionStrategy strategy = strategies.get(kind);
        if (strategy == null) {
            log.error("No ConnectionStrategy bean for kind={} but routing accepted it — wiring bug",
                kind);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                errorBody(kind.name(), "no_strategy",
                    "No ConnectionStrategy registered for kind=" + kind)
            );
        }

        // ── 6. Find or create in-flight Connection ────────────────────────────
        Connection connection = callbackService.findOrCreatePendingConnection(binding.workspaceId(), kind);
        IntegrationRef ref = connection.toRef();

        // ── 7. Hand off to strategy.finalizeConnect ──────────────────────────
        ConnectFinalization result;
        try {
            // Defensive copy + remove the state param — strategies shouldn't see it
            // and we don't want it accidentally logged twice.
            Map<String, String> callbackParams = new HashMap<>(allParams == null ? Map.of() : allParams);
            callbackParams.remove("state");
            result = strategy.finalizeConnect(ref, callbackParams);
        } catch (RuntimeException e) {
            log.warn("Strategy.finalizeConnect threw for kind={} workspace={}: {}",
                kind, binding.workspaceId(), e.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), "strategy_error", e.getMessage())
            );
        }

        return switch (result) {
            case ConnectFinalization.Completed c -> handleCompleted(connection, c, binding, kind);
            case ConnectFinalization.Failed f -> handleFailed(kind, binding, f);
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<?> handleCompleted(
        Connection connection,
        ConnectFinalization.Completed completed,
        StateBinding binding,
        IntegrationKind kind
    ) {
        try {
            callbackService.completeConnection(connection, completed, binding.actorRef());
        } catch (IllegalStateException e) {
            // Transition guard rejection (e.g. UNINSTALLED → ACTIVE). Unusual but
            // possible if a webhook UNINSTALLED the row between create and finalize.
            log.warn("OAuth complete rejected by transition guard for connection={}: {}",
                connection.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                errorBody(kind.name(), "transition_conflict", e.getMessage())
            );
        }
        return redirect(properties.successRedirect());
    }

    private ResponseEntity<?> handleFailed(
        IntegrationKind kind,
        StateBinding binding,
        ConnectFinalization.Failed failed
    ) {
        log.warn("OAuth callback failed for kind={} workspace={}: {}",
            kind, binding.workspaceId(), failed.reason());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            errorBody(kind.name(), "finalize_failed", failed.reason())
        );
    }

    private ResponseEntity<?> redirect(String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Build a structured error body for 4xx responses. Wrapped as a method to keep
     * the JSON shape consistent across every error branch — clients (browser dev
     * tools, proxy logs) get a stable contract.
     */
    private static Map<String, String> errorBody(@Nullable String kind, String error, @Nullable String description) {
        Map<String, String> body = new HashMap<>();
        body.put("kind", kind == null ? "unknown" : kind);
        body.put("error", error);
        if (description != null) body.put("errorDescription", description);
        return Collections.unmodifiableMap(body);
    }

    /**
     * Strip control characters and cap length before logging user-controlled input.
     * Prevents log injection (CRLF) and giant payloads dragging down the log volume.
     */
    @Nullable
    private static String sanitize(@Nullable String input) {
        if (input == null) return null;
        String stripped = input.replaceAll("[\\p{Cntrl}]", "_");
        return stripped.length() > 200 ? stripped.substring(0, 200) + "…" : stripped;
    }

    /**
     * Lowercase rendering of the kind for URL substitution. Exposed for the
     * {@link OAuthCallbackProperties#errorRedirect()} {@code {kind}} placeholder if it
     * ever needs to be applied in this controller.
     */
    @SuppressWarnings("unused")
    private static String lowercaseKind(IntegrationKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
