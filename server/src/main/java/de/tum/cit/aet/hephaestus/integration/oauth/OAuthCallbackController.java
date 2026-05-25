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
 * Vendor OAuth redirect ingress at {@code /oauth/callback/{kind}}. Unauthenticated —
 * the browser arrives without a Hephaestus session — so identity comes from the
 * HMAC-signed {@code state} param ({@code (workspaceId, kind, issuedAt, actorRef)},
 * minted by {@link OAuthStateService}). The path's {@code {kind}} MUST equal the
 * state's kind, otherwise a state issued for kind A could be replayed against kind B's
 * callback.
 *
 * <p>Browser audience: success and vendor-side cancellations return 302 redirects.
 * Framework errors (bad state, unknown kind, strategy throw) return 4xx JSON so dev
 * tools surface the failure clearly.
 *
 * <p>Repository access lives in {@link OAuthCallbackService} (architecture rule). The
 * workspace-context allow-list exempts this controller — workspace identity is from
 * the verified state, never from the request.
 */
@RestController
@RequestMapping("/oauth/callback")
public class OAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackController.class);

    /**
     * Reserved callbackParams key carrying the PKCE {@code code_verifier} from the
     * issuing flow's nonce row. Per-vendor strategies pull this out and add it as
     * {@code code_verifier} on the token-exchange POST per RFC 7636 §4.5. Prefixed
     * with {@code _} so it cannot collide with a vendor-supplied query param.
     */
    public static final String PKCE_VERIFIER_PARAM = "_pkce_verifier";

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
     * GET callback (Slack, Outline, GitHub App install). The {@code error} param is
     * checked before state so vendor cancellations ({@code error=access_denied} with
     * no {@code code}) surface as 400 rather than blowing up on missing state.
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

    /** POST variant for vendors that send form-encoded callbacks. */
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

    /** Shared core for GET + POST callbacks. Package-visible for unit tests. */
    ResponseEntity<?> handleCallback(
        String kindPathSegment,
        @Nullable String code,
        @Nullable String state,
        @Nullable String vendorError,
        @Nullable String vendorErrorDescription,
        Map<String, String> allParams
    ) {
        Optional<IntegrationKind> kindOpt = kindRouting.resolve(kindPathSegment);
        if (kindOpt.isEmpty()) {
            log.info("OAuth callback for unknown kind path segment: {}", sanitize(kindPathSegment));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                errorBody(kindPathSegment, "unknown_kind",
                    "No integration registered for path segment: " + sanitize(kindPathSegment))
            );
        }
        IntegrationKind kind = kindOpt.get();

        // The state may still be present and valid; we leave it usable (it'll expire
        // naturally via TTL) rather than racing the user who might immediately retry.
        if (vendorError != null && !vendorError.isBlank()) {
            log.info("OAuth callback for kind={} returned vendor error={} description={}",
                kind, sanitize(vendorError), sanitize(vendorErrorDescription));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), vendorError, vendorErrorDescription)
            );
        }

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

        if (binding.kind() != kind) {
            log.warn("OAuth state-kind mismatch: path={} stateKind={} workspace={}",
                kind, binding.kind(), binding.workspaceId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                errorBody(kind.name(), "kind_mismatch",
                    "State issued for kind=" + binding.kind() + " replayed against callback path /oauth/callback/" + kindPathSegment)
            );
        }

        ConnectionStrategy strategy = strategies.get(kind);
        if (strategy == null) {
            log.error("No ConnectionStrategy bean for kind={} but routing accepted it — wiring bug",
                kind);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                errorBody(kind.name(), "no_strategy",
                    "No ConnectionStrategy registered for kind=" + kind)
            );
        }

        Connection connection = callbackService.findOrCreatePendingConnection(binding.workspaceId(), kind);
        IntegrationRef ref = connection.toRef();

        ConnectFinalization result;
        try {
            // Defensive copy + remove the state param — strategies shouldn't see it
            // and we don't want it accidentally logged twice. Inject the PKCE verifier
            // (if the state was PKCE-issued) under a reserved key so the strategy
            // adds it as `code_verifier` on the token-exchange POST per RFC 7636 §4.5.
            Map<String, String> callbackParams = new HashMap<>(allParams == null ? Map.of() : allParams);
            callbackParams.remove("state");
            if (binding.codeVerifier() != null) {
                callbackParams.put(PKCE_VERIFIER_PARAM, binding.codeVerifier());
            }
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

    /** Structured 4xx body: stable {kind, error, errorDescription} shape across all branches. */
    private static Map<String, String> errorBody(@Nullable String kind, String error, @Nullable String description) {
        Map<String, String> body = new HashMap<>();
        body.put("kind", kind == null ? "unknown" : kind);
        body.put("error", error);
        if (description != null) body.put("errorDescription", description);
        return Collections.unmodifiableMap(body);
    }

    /** Strip control characters + cap length so user-supplied input can't poison logs. */
    @Nullable
    private static String sanitize(@Nullable String input) {
        if (input == null) return null;
        String stripped = input.replaceAll("[\\p{Cntrl}]", "_");
        return stripped.length() > 200 ? stripped.substring(0, 200) + "…" : stripped;
    }

}
