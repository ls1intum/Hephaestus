package de.tum.cit.aet.hephaestus.integration.core.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.core.oauth.state.OAuthStateService.StateBinding;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.ConnectFinalization;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionStrategy.ConnectInitiation;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.webhook.IntegrationKindRouting;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pure unit tests for {@link OAuthCallbackController} — no Spring context, no MockMvc.
 *
 * <p>Verifies the security-critical paths (state HMAC mismatch, kind mismatch, vendor
 * cancellation) AND the happy path (state consumed → strategy finalize → PENDING →
 * ACTIVE transition → 302 to success URL). The Spring routing layer + the
 * {@code @PreAuthorize("permitAll()")} wiring are covered by the architecture tests
 * + the SecurityConfig {@code securityMatcher} that pins {@code /oauth/callback/**}
 * to the permit-all worker chain.
 */
@DisplayName("OAuthCallbackController — unit")
class OAuthCallbackControllerTest extends BaseUnitTest {

    private static final OAuthCallbackProperties PROPS = new OAuthCallbackProperties("/integrations?status=success");

    @Mock
    private OAuthStateService oauthStateService;

    @Mock
    private OAuthCallbackService callbackService;

    private IntegrationKindRouting routing;
    private FakeStrategy slackStrategy;
    private FakeStrategy outlineStrategy;
    private OAuthCallbackController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Real routing — its behaviour is unit-tested separately; using the real bean
        // here gives the test exact parity with production path resolution.
        routing = new IntegrationKindRouting();
        slackStrategy = new FakeStrategy(IntegrationKind.SLACK);
        outlineStrategy = new FakeStrategy(IntegrationKind.OUTLINE);
        controller = new OAuthCallbackController(
            routing,
            oauthStateService,
            callbackService,
            List.of(slackStrategy, outlineStrategy),
            PROPS
        );
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /oauth/callback/slack: state consumed → finalize Completed → upsert + ACTIVE transition → 302")
    void happyPath_slackCompleted_transitionsAndRedirects() {
        String state = "signed-state-token";
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice@example.com");
        when(oauthStateService.consume(state)).thenReturn(binding);

        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);
        when(callbackService.completeConnection(any(Connection.class), any(), any())).thenAnswer(inv -> {
            Connection c = inv.getArgument(0);
            c.setState(IntegrationState.ACTIVE);
            return c;
        });

        slackStrategy.nextFinalization = new ConnectFinalization.Completed(
            "T123ABC",
            new BearerToken("xoxb-fake", null),
            "Acme Workspace"
        );

        Map<String, String> allParams = new HashMap<>();
        allParams.put("code", "vendor-code");
        allParams.put("state", state);

        ResponseEntity<?> response = controller.callbackGet("slack", "vendor-code", state, null, null, allParams);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo(PROPS.successRedirect());

        ArgumentCaptor<ConnectFinalization.Completed> completed = ArgumentCaptor.forClass(
            ConnectFinalization.Completed.class
        );
        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(callbackService).completeConnection(eq(pending), completed.capture(), actor.capture());
        assertThat(completed.getValue().instanceKey()).isEqualTo("T123ABC");
        assertThat(completed.getValue().displayName()).isEqualTo("Acme Workspace");
        assertThat(actor.getValue()).isEqualTo("alice@example.com");

        // The strategy's finalize must NOT see the state param — the controller scrubs it
        // before handoff so vendors don't accidentally double-log.
        assertThat(slackStrategy.lastCallbackParams).doesNotContainKey("state");
        assertThat(slackStrategy.lastCallbackParams).containsEntry("code", "vendor-code");
    }

    @Test
    @DisplayName("Null actorRef in state binding is passed through verbatim — fallback lives in OAuthCallbackService")
    void happyPath_nullActorRef_passedThrough() {
        String state = "signed-state-token";
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), null);
        when(oauthStateService.consume(state)).thenReturn(binding);
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);
        when(callbackService.completeConnection(any(Connection.class), any(), any())).thenReturn(pending);
        slackStrategy.nextFinalization = new ConnectFinalization.Completed("T123", new BearerToken("tok", null), null);

        controller.callbackGet("slack", "c", state, null, null, Map.of("code", "c", "state", state));

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(callbackService).completeConnection(any(Connection.class), any(), actor.capture());
        assertThat(actor.getValue()).isNull();
    }

    // ── Vendor-side error ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /oauth/callback/slack?error=access_denied → 400 with JSON {kind, error, errorDescription}")
    void vendorError_returns400WithStructuredJson() {
        ResponseEntity<?> response = controller.callbackGet(
            "slack",
            /* code */ null,
            /* state */ null,
            "access_denied",
            "The user denied the request",
            Map.of("error", "access_denied", "error_description", "The user denied the request")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("kind", "SLACK");
        assertThat(body).containsEntry("error", "access_denied");
        assertThat(body).containsEntry("errorDescription", "The user denied the request");

        // State was never consumed — the user might retry without re-issuing.
        verify(oauthStateService, never()).consume(any());
        // Strategy never called.
        assertThat(slackStrategy.finalizeCalls).isEqualTo(0);
        verify(callbackService, never()).findOrCreatePendingConnection(anyLong(), any());
    }

    // ── State validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing state → 400")
    void missingState_returns400() {
        ResponseEntity<?> response = controller.callbackGet("slack", "code", null, null, null, Map.of("code", "code"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "missing_state");
    }

    @Test
    @DisplayName("Bad state (HMAC mismatch / expired) → 400 with reason from OAuthStateService")
    void badState_returns400() {
        when(oauthStateService.consume("tampered")).thenThrow(
            new IllegalArgumentException("OAuth state signature mismatch")
        );

        ResponseEntity<?> response = controller.callbackGet(
            "slack",
            "c",
            "tampered",
            null,
            null,
            Map.of("code", "c", "state", "tampered")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "invalid_state");
        assertThat(body.get("errorDescription")).contains("signature mismatch");
        // Critical: strategy NEVER invoked when the state is bad.
        assertThat(slackStrategy.finalizeCalls).isEqualTo(0);
        verify(callbackService, never()).findOrCreatePendingConnection(anyLong(), any());
    }

    @Test
    @DisplayName("State kind ≠ path kind (cross-kind replay) → 400 with explanation")
    void stateKindMismatch_returns400() {
        // State issued for SLACK; replayed against /oauth/callback/outline.
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("slack-state")).thenReturn(binding);

        ResponseEntity<?> response = controller.callbackGet(
            "outline",
            "c",
            "slack-state",
            null,
            null,
            Map.of("code", "c", "state", "slack-state")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "kind_mismatch");
        assertThat(body.get("errorDescription")).contains("SLACK").contains("outline");
        assertThat(outlineStrategy.finalizeCalls).isEqualTo(0);
        assertThat(slackStrategy.finalizeCalls).isEqualTo(0);
        verify(callbackService, never()).findOrCreatePendingConnection(anyLong(), any());
    }

    // ── Strategy failure ────────────────────────────────────────────────────

    @Test
    @DisplayName("Strategy.finalizeConnect returns Failed → 400 with reason")
    void finalizeFailed_returns400() {
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("s")).thenReturn(binding);
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);

        slackStrategy.nextFinalization = new ConnectFinalization.Failed("vendor rejected the code");

        ResponseEntity<?> response = controller.callbackGet(
            "slack",
            "c",
            "s",
            null,
            null,
            Map.of("code", "c", "state", "s")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "finalize_failed");
        assertThat(body.get("errorDescription")).contains("vendor rejected");
        // No completion issued on a Failed result.
        verify(callbackService, never()).completeConnection(any(), any(), any());
    }

    @Test
    @DisplayName("Strategy.finalizeConnect throws → 400 strategy_error")
    void finalizeThrows_returns400() {
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("s")).thenReturn(binding);
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);

        slackStrategy.throwOnFinalize = new RuntimeException("vendor 500");

        ResponseEntity<?> response = controller.callbackGet(
            "slack",
            "c",
            "s",
            null,
            null,
            Map.of("code", "c", "state", "s")
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "strategy_error");
    }

    // ── Transition guard rejection ──────────────────────────────────────────

    @Test
    @DisplayName("completeConnection throws IllegalStateException → 409 transition_conflict")
    void completeConnection_transitionGuardRejects_returns409() {
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("s")).thenReturn(binding);
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);
        slackStrategy.nextFinalization = new ConnectFinalization.Completed("T1", new BearerToken("t", null), null);
        when(callbackService.completeConnection(any(), any(), any())).thenThrow(
            new IllegalStateException("Illegal transition for connection 7: UNINSTALLED → ACTIVE")
        );

        ResponseEntity<?> response = controller.callbackGet(
            "slack",
            "c",
            "s",
            null,
            null,
            Map.of("code", "c", "state", "s")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "transition_conflict");
    }

    // ── Unknown kind ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unknown kind path segment → 404")
    void unknownKind_returns404() {
        ResponseEntity<?> response = controller.callbackGet(
            "bitbucket",
            "c",
            "s",
            null,
            null,
            Map.of("code", "c", "state", "s")
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "unknown_kind");
        verify(oauthStateService, never()).consume(any());
    }

    // ── No strategy registered ──────────────────────────────────────────────

    @Test
    @DisplayName("Kind allow-listed but no strategy bean → 500 (configuration bug)")
    void noStrategy_returns500() {
        // Build a controller with NO strategies registered.
        OAuthCallbackController bare = new OAuthCallbackController(
            routing,
            oauthStateService,
            callbackService,
            List.of(),
            PROPS
        );
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("s")).thenReturn(binding);

        ResponseEntity<?> response = bare.callbackGet("slack", "c", "s", null, null, Map.of("code", "c", "state", "s"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "no_strategy");
    }

    // ── POST callback ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /oauth/callback/slack delegates through the same shared handler")
    void postCallback_sharesGetHandler() {
        StateBinding binding = new StateBinding(42L, IntegrationKind.SLACK, Instant.now(), "alice");
        when(oauthStateService.consume("s")).thenReturn(binding);
        Connection pending = newConnection(7L, 42L, IntegrationKind.SLACK, null, IntegrationState.PENDING);
        when(callbackService.findOrCreatePendingConnection(42L, IntegrationKind.SLACK)).thenReturn(pending);
        when(callbackService.completeConnection(any(), any(), any())).thenReturn(pending);
        slackStrategy.nextFinalization = new ConnectFinalization.Completed("T1", new BearerToken("t", null), null);

        ResponseEntity<?> response = controller.callbackPost(
            "slack",
            "c",
            "s",
            null,
            null,
            Map.of("code", "c", "state", "s")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo(PROPS.successRedirect());
        verify(callbackService).completeConnection(any(), any(), any());
    }

    // ── Duplicate strategy guard ────────────────────────────────────────────

    @Test
    @DisplayName("Constructor rejects duplicate ConnectionStrategy beans for the same kind")
    void duplicateStrategy_throwsAtWiringTime() {
        FakeStrategy a = new FakeStrategy(IntegrationKind.SLACK);
        FakeStrategy b = new FakeStrategy(IntegrationKind.SLACK);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new OAuthCallbackController(routing, oauthStateService, callbackService, List.of(a, b), PROPS)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate ConnectionStrategy");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Connection newConnection(
        long id,
        long workspaceId,
        IntegrationKind kind,
        String instanceKey,
        IntegrationState state
    ) {
        Workspace ws = Mockito.mock(Workspace.class);
        Mockito.lenient().when(ws.getId()).thenReturn(workspaceId);
        ConnectionConfig cfg = switch (kind) {
            case GITHUB -> new ConnectionConfig.GitHubAppConfig(null, null, null, java.util.Set.of());
            case GITLAB -> new ConnectionConfig.GitLabConfig(
                "https://gitlab.com",
                null,
                null,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                java.util.Set.of()
            );
            case SLACK -> new ConnectionConfig.SlackConfig(null, null, null, null, java.util.Set.of());
            case OUTLINE -> new ConnectionConfig.OutlineConfig("https://app.getoutline.com", null, java.util.Set.of());
        };
        Connection c = new Connection(ws, kind, instanceKey, cfg);
        c.setState(state);
        setId(c, id);
        return c;
    }

    private static void setId(Connection c, long id) {
        try {
            Field idField = Connection.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(c, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Per-kind ConnectionStrategy stub. Tests dictate the {@code nextFinalization} (or
     * {@code throwOnFinalize}); call counters let us assert the strategy was — or was
     * NOT — invoked.
     */
    static final class FakeStrategy implements ConnectionStrategy {

        private final IntegrationKind kind;
        ConnectFinalization nextFinalization;
        RuntimeException throwOnFinalize;
        int finalizeCalls = 0;
        Map<String, String> lastCallbackParams;

        FakeStrategy(IntegrationKind kind) {
            this.kind = kind;
        }

        @Override
        public IntegrationKind kind() {
            return kind;
        }

        @Override
        public ConnectInitiation initiate(InitiateRequest request) {
            throw new UnsupportedOperationException("initiate() not exercised by OAuthCallbackController");
        }

        @Override
        public ConnectFinalization finalizeConnect(IntegrationRef ref, Map<String, String> callbackParams) {
            finalizeCalls++;
            lastCallbackParams = callbackParams;
            if (throwOnFinalize != null) throw throwOnFinalize;
            if (nextFinalization == null) {
                throw new IllegalStateException("test forgot to set nextFinalization");
            }
            return nextFinalization;
        }

        @Override
        public ValidationResult validate(IntegrationRef ref, CredentialBundle credentials) {
            return new ValidationResult.Ok(null, null);
        }

        @Override
        public void revoke(IntegrationRef ref) {
            // unused in this controller
        }
    }
}
