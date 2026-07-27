package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class LlmProxyControllerTest extends BaseUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private LlmModelResolver resolver;

    @Mock
    private EgressPolicy egressPolicy;

    @Mock
    private ProxyBudgetGate budgetGate;

    @Mock
    private ProxyUsageAccumulator usageAccumulator;

    @Mock
    private MentorTurnUsageAccumulator mentorTurnUsageAccumulator;

    private LlmProxyController controller;

    @BeforeEach
    void setUp() {
        // budgetGate mock defaults isBlocked() to false, so existing tests see an open gate.
        controller = new LlmProxyController(
            WebClient.create(),
            resolver,
            egressPolicy,
            OBJECT_MAPPER,
            new ProxyAccounting(
                budgetGate,
                usageAccumulator,
                mentorTurnUsageAccumulator,
                new SimpleMeterRegistry(),
                OBJECT_MAPPER
            )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class BudgetGate {

        /**
         * The 429 body names WHICH purse stopped the call — the two have different remedies and
         * different people behind them. Which purse the gate consults is
         * {@code ProxyBudgetGateTest}'s subject; here the routing's funding source is what the body
         * must be worded from.
         *
         * <p>The status alone proves the credential was never resolved: a call that reaches the
         * resolver gets nothing back from it and returns 502, as {@link #doesNotRejectACallTheGateAllows}
         * shows.
         */
        @Test
        void the429BodyNamesTheSharedPurseForASharedModelCall() {
            var routing = routing("openai-completions");
            authenticate(routing);
            when(budgetGate.isBlocked(routing)).thenReturn(true);

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(429);
            assertThat(String.valueOf(result.getBody()))
                .contains("Shared-model budget reached")
                .contains("raises the budget");
        }

        @Test
        void the429BodyNamesTheOwnProviderPurseForAnOwnProviderCall() {
            var routing = workspaceFundedRouting();
            authenticate(routing);
            when(budgetGate.isBlocked(routing)).thenReturn(true);

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(429);
            assertThat(String.valueOf(result.getBody()))
                .contains("Own-provider budget reached")
                .contains("raises the cap");
        }

        /**
         * A call the gate allows is never turned into a 429 further down: it proceeds to credential
         * resolution (which here returns nothing, hence 502 — anything but 429 proves it went
         * through). The asymmetry that produces the allow — an exhausted host budget must not stop a
         * call the workspace pays for — is decided inside the gate, and pinned by
         * {@code ProxyBudgetGateTest}.
         */
        @Test
        void doesNotRejectACallTheGateAllows() {
            var routing = workspaceFundedRouting();
            authenticate(routing);
            when(budgetGate.isBlocked(routing)).thenReturn(false);
            when(resolver.resolveProxyCredential(any())).thenReturn(null);

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }
    }

    /**
     * A mentor sandbox outlives the turns that use it, so its credential stays valid between them.
     * A call that arrives in that gap names no execution — and an unnamed call is one no accumulator
     * can write down, since both of them key on the attempt.
     */
    @Nested
    class CallsWithNoBillingTarget {

        @Test
        void refusesAMentorCallMadeBetweenTurnsRatherThanServingItUnbilled() {
            authenticate(unboundMentorSessionRouting());

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(403);
            // Never resolved a credential, so it never reached a provider: refused before the money,
            // not after it.
            verifyNoInteractions(resolver);
            verifyNoInteractions(mentorTurnUsageAccumulator);
            verifyNoInteractions(usageAccumulator);
        }

        /** The budget gate has nothing to judge without an attempt, and must not be reached first. */
        @Test
        void refusesBeforeConsultingTheBudgetGate() {
            authenticate(unboundMentorSessionRouting());

            controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            verifyNoInteractions(budgetGate);
        }
    }

    @Nested
    class SafeSurface {

        @Test
        void shouldRejectNonPostBeforeCredentialResolution() {
            authenticate(routing("openai-completions"));
            var request = request("GET", "/internal/llm/chat/completions");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result.getStatusCode().value()).isEqualTo(405);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectWrongPathBeforeCredentialResolution() {
            authenticate(routing("openai-completions"));
            var request = request("POST", "/internal/llm/models");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectQueryBeforeCredentialResolution() {
            authenticate(routing("openai-responses"));
            var request = request("POST", "/internal/llm/responses");
            request.setQueryString("api-version=unsafe");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectProtocolPathMismatchBeforeCredentialResolution() {
            authenticate(routing("openai-responses"));
            var request = request("POST", "/internal/llm/chat/completions");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldAcceptChatCompletionsPath() {
            var routing = routing("openai-completions");
            authenticate(routing);
            stubCredential(routing, credential("openai-completions", LlmAuthMode.BEARER));
            doThrow(new IllegalArgumentException("blocked")).when(egressPolicy).validate("https://api.example.com/v1");

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        void shouldAcceptResponsesPath() {
            var routing = routing("openai-responses");
            authenticate(routing);
            stubCredential(routing, credential("openai-responses", LlmAuthMode.API_KEY));
            doThrow(new IllegalArgumentException("blocked")).when(egressPolicy).validate("https://api.example.com/v1");

            var result = controller.proxy(
                request("POST", "/internal/llm/responses"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        void shouldRejectWhenLiveConnectionProtocolDiffersFromFrozenRouting() {
            var routing = routing("openai-completions");
            authenticate(routing);
            stubCredential(routing, credential("openai-responses", LlmAuthMode.BEARER));

            var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody()
            );

            assertThat(result.getStatusCode().value()).isEqualTo(502);
            verifyNoInteractions(egressPolicy);
        }
    }

    @Nested
    class BodyLocking {

        @Test
        void shouldForceAuthoritativeModel() {
            byte[] input = "{\"model\":\"runner-controlled\",\"service_tier\":\"priority\",\"messages\":[]}".getBytes(
                StandardCharsets.UTF_8
            );

            var prepared = controller.prepareBody(input, "catalog-model", false);

            var tree = OBJECT_MAPPER.readTree(prepared.body());
            assertThat(tree.path("model").asString()).isEqualTo("catalog-model");
            assertThat(tree.has("service_tier")).isFalse();
        }

        @Test
        void shouldRejectMalformedJson() {
            assertThat(controller.prepareBody("not-json".getBytes(StandardCharsets.UTF_8), "model", false)).isNull();
        }

        @Test
        void shouldRejectJsonThatIsNotAnObject() {
            assertThat(controller.prepareBody("[]".getBytes(StandardCharsets.UTF_8), "model", false)).isNull();
        }

        @Test
        void shouldAddStreamingUsageForChatCompletions() {
            byte[] input = "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8);

            var prepared = controller.prepareBody(input, "catalog-model", true);

            var tree = OBJECT_MAPPER.readTree(prepared.body());
            assertThat(tree.path("model").asString()).isEqualTo("catalog-model");
            assertThat(tree.path("stream_options").path("include_usage").asBoolean()).isTrue();
            // The un-augmented body is kept so a provider that rejects the flag can be retried on
            // exactly what the caller sent.
            assertThat(OBJECT_MAPPER.readTree(prepared.withoutUsageRequest()).has("stream_options")).isFalse();
        }

        /**
         * The surface a runner may ask the provider for. A rejected body is answered before any
         * upstream call, so each {@code false} row is a capability the sandbox cannot buy on the
         * host's key, and each {@code true} row is one it must keep.
         */
        static Stream<Arguments> requestedCapabilities() {
            return Stream.of(
                Arguments.of("{\"tools\":[{\"type\":\"function\"},{\"type\":\"custom\"}]}", true, "caller-run tools"),
                Arguments.of("{\"tools\":[{\"type\":\"web_search_preview\"}]}", false, "a provider-hosted tool"),
                Arguments.of("{\"web_search_options\":{}}", false, "hosted search asked for outside tools"),
                Arguments.of("{\"modalities\":[\"text\",\"audio\"],\"audio\":{}}", false, "audio output"),
                Arguments.of("{\"modalities\":[\"text\"]}", true, "an explicit text-only modality")
            );
        }

        @ParameterizedTest(name = "{2}")
        @MethodSource("requestedCapabilities")
        void locksTheRequestedCapabilitySurface(String body, boolean allowed, String what) {
            var prepared = controller.prepareBody(body.getBytes(StandardCharsets.UTF_8), "model", false);

            if (allowed) {
                assertThat(prepared).as(what).isNotNull();
            } else {
                assertThat(prepared).as(what).isNull();
            }
        }
    }

    /**
     * A streamed call, end to end against a real upstream: what the client receives, and what the
     * ledger is told, from the same bytes.
     *
     * <p>These tests drive an actual HTTP stream because the failure they guard against is invisible to
     * any mock: a controller that returns the instant it sees an SSE body serves a perfect response and
     * reaches no accounting at all, so only the ledger tells the two apart.
     */
    @Nested
    class Streaming {

        private MockWebServer upstream;
        private LlmProxyController streamingController;

        @BeforeEach
        void startUpstream() throws IOException {
            upstream = new MockWebServer();
            upstream.start();
            streamingController = new LlmProxyController(
                WebClient.builder().build(),
                resolver,
                egressPolicy,
                OBJECT_MAPPER,
                new ProxyAccounting(
                    budgetGate,
                    usageAccumulator,
                    mentorTurnUsageAccumulator,
                    new SimpleMeterRegistry(),
                    OBJECT_MAPPER
                )
            );
        }

        @AfterEach
        void stopUpstream() throws IOException {
            upstream.close();
        }

        private ProxyRouting streamingRouting() {
            return new ProxyRouting(
                "job:stream",
                "openai-completions",
                upstream.url("/v1").toString(),
                FundingSource.INSTANCE,
                7L,
                8L,
                9L,
                ATTEMPT
            );
        }

        private MockHttpServletResponse proxyStream(String... sseFrames) {
            StringBuilder body = new StringBuilder();
            for (String frame : sseFrames) {
                body.append("data: ").append(frame).append("\n\n");
            }
            upstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(body.toString())
                    .build()
            );
            ProxyRouting routing = streamingRouting();
            authenticate(routing);
            when(
                resolver.resolveProxyCredential(
                    new LlmModelResolver.ConnectionRef(
                        routing.connectionScope(),
                        routing.connectionId(),
                        routing.modelId(),
                        routing.workspaceId()
                    )
                )
            ).thenReturn(
                new LlmModelResolver.ProxyCredential(
                    upstream.url("/v1").toString(),
                    "openai-completions",
                    LlmAuthMode.BEARER,
                    "catalog-model",
                    "secret"
                )
            );
            MockHttpServletResponse response = new MockHttpServletResponse();
            controllerProxy(response);
            return response;
        }

        private void controllerProxy(MockHttpServletResponse response) {
            streamingController.proxy(
                request("POST", "/internal/llm/chat/completions"),
                response,
                new HttpHeaders(),
                "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8)
            );
        }

        /**
         * Kills "return before recording usage on the SSE branch" — i.e. the original bug. The client's
         * bytes are asserted alongside the accounting so a fix that buffered the stream to read it would
         * not pass either.
         */
        @Test
        @DisplayName("a streamed call is billed for the usage its final frame reports")
        void aStreamedCallIsBilled() throws Exception {
            MockHttpServletResponse response = proxyStream(
                "{\"choices\":[{\"delta\":{\"content\":\"hi\"}}],\"usage\":null}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":40," +
                    "\"prompt_tokens_details\":{\"cached_tokens\":25}}}",
                "[DONE]"
            );

            assertThat(response.getContentAsString()).contains("\"content\":\"hi\"").contains("[DONE]");
            ArgumentCaptor<ProxyTokenUsage> usage = ArgumentCaptor.forClass(ProxyTokenUsage.class);
            verify(usageAccumulator).accumulate(eq(ATTEMPT), usage.capture());
            assertThat(usage.getValue().billableInputTokens()).isEqualTo(75);
            assertThat(usage.getValue().outputTokens()).isEqualTo(40);
            assertThat(usage.getValue().cacheReadTokens()).isEqualTo(25);
        }

        /**
         * A stream that ends before its usage frame bills what it observed — which is nothing. Recording
         * a zero-token call instead would put a fabricated event in the ledger.
         */
        @Test
        @DisplayName("a stream that terminates before the usage frame records nothing")
        void aTruncatedStreamRecordsNothing() throws Exception {
            MockHttpServletResponse response = proxyStream(
                "{\"choices\":[{\"delta\":{\"content\":\"cut off here\"}}],\"usage\":null}"
            );

            assertThat(response.getContentAsString()).contains("cut off here");
            verifyNoInteractions(usageAccumulator);
        }

        /**
         * The request-side half of the fix, proven on the wire: without the flag the provider sends no
         * usage at all and there is nothing for the tap to read.
         */
        @Test
        @DisplayName("the outgoing streaming request asks the provider to report usage")
        void theOutgoingRequestAsksForUsage() throws Exception {
            proxyStream("[DONE]");

            RecordedRequest sent = upstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(sent).isNotNull();
            assertThat(
                OBJECT_MAPPER.readTree(sent.getBody().utf8()).path("stream_options").path("include_usage").asBoolean()
            ).isTrue();
        }

        /**
         * Asking for usage is the proxy's own addition to the caller's payload, so a provider that
         * refuses the field must cost the caller nothing more than our blindness to that call's tokens.
         *
         * <p>Kills "drop the retry-without-stream_options fallback": every streaming call against such a
         * provider then fails with a 400 the runner never asked for.
         */
        @Test
        @DisplayName("a provider that rejects stream_options degrades to an unmetered call, not a failure")
        void aProviderThatRejectsTheUsageRequestDegrades() throws Exception {
            upstream.enqueue(
                new MockResponse.Builder()
                    .code(400)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"error\":{\"message\":\"Unrecognized request argument: stream_options\"}}")
                    .build()
            );
            upstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: {\"choices\":[{\"delta\":{\"content\":\"served anyway\"}}]}\n\n")
                    .build()
            );
            ProxyRouting routing = streamingRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential(
                    upstream.url("/v1").toString(),
                    "openai-completions",
                    LlmAuthMode.BEARER,
                    "catalog-model",
                    "secret"
                )
            );
            MockHttpServletResponse response = new MockHttpServletResponse();

            controllerProxy(response);

            assertThat(response.getContentAsString()).contains("served anyway");
            assertThat(upstream.takeRequest(5, TimeUnit.SECONDS).getBody().utf8()).contains("stream_options");
            assertThat(upstream.takeRequest(5, TimeUnit.SECONDS).getBody().utf8()).doesNotContain("stream_options");
        }
    }

    @Nested
    class HeaderAllowlist {

        @Test
        void shouldInjectBearerAuthAndDropUnapprovedHeaders() {
            var incoming = incomingHeaders();

            HttpHeaders output = controller.buildUpstreamHeaders(
                incoming,
                credential("openai-completions", LlmAuthMode.BEARER)
            );

            assertThat(output.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret");
            assertThat(output.get("api-key")).isNull();
            assertThat(output.get("x-api-key")).isNull();
            assertThat(output.get("x-forward-me")).isNull();
            assertThat(output.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
            assertThat(output.getFirst(HttpHeaders.ACCEPT)).isEqualTo("text/event-stream");
            assertThat(output.getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("identity");
        }

        @Test
        void shouldInjectRawApiKeyAuth() {
            HttpHeaders output = controller.buildUpstreamHeaders(
                incomingHeaders(),
                credential("openai-responses", LlmAuthMode.API_KEY)
            );

            assertThat(output.getFirst("api-key")).isEqualTo("secret");
            assertThat(output.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldNotInjectAuthWhenKeyIsBlank() {
            var credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com/v1",
                "openai-completions",
                LlmAuthMode.BEARER,
                "catalog-model",
                " "
            );

            HttpHeaders output = controller.buildUpstreamHeaders(incomingHeaders(), credential);

            assertThat(output.get(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(output.get("api-key")).isNull();
        }
    }

    @Test
    void shouldBuildCanonicalProtocolUrls() {
        assertThat(LlmProxyController.buildUpstreamUri("https://api.example.com/v1/", "openai-completions")).isEqualTo(
            java.net.URI.create("https://api.example.com/v1/chat/completions")
        );
        assertThat(LlmProxyController.buildUpstreamUri("https://api.example.com/v1", "openai-responses")).isEqualTo(
            java.net.URI.create("https://api.example.com/v1/responses")
        );
    }

    private static byte[] jsonBody() {
        return "{\"model\":\"anything\"}".getBytes(StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static HttpHeaders incomingHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer sandbox-token");
        headers.set("api-key", "sandbox-token");
        headers.set("x-api-key", "sandbox-token");
        headers.set("x-forward-me", "unsafe");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "text/event-stream");
        return headers;
    }

    /** One running attempt of one job, with nothing spent yet — the gate is stubbed in these tests. */
    private static final ProxyRouting.BilledAttempt ATTEMPT = new ProxyRouting.BilledAttempt(
        de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType.AGENT_JOB,
        java.util.UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
        0,
        java.math.BigDecimal.ZERO
    );

    private static ProxyRouting routing(String protocol) {
        return new ProxyRouting(
            "job:test",
            protocol,
            "https://frozen.example.com/v1",
            FundingSource.INSTANCE,
            7L,
            8L,
            9L,
            ATTEMPT
        );
    }

    /** The same route, but billed to the workspace's own provider rather than a shared model. */
    private static ProxyRouting workspaceFundedRouting() {
        return new ProxyRouting(
            "job:test",
            "openai-completions",
            "https://frozen.example.com/v1",
            FundingSource.WORKSPACE,
            7L,
            8L,
            9L,
            ATTEMPT
        );
    }

    /** A live mentor sandbox credential with no turn bound to it — the one routing with no attempt. */
    private static ProxyRouting unboundMentorSessionRouting() {
        return new ProxyRouting(
            "mentor-session",
            "openai-completions",
            "https://frozen.example.com/v1",
            FundingSource.INSTANCE,
            7L,
            8L,
            9L,
            null
        );
    }

    private static LlmModelResolver.ProxyCredential credential(String protocol, LlmAuthMode authMode) {
        return new LlmModelResolver.ProxyCredential(
            "https://api.example.com/v1",
            protocol,
            authMode,
            "catalog-model",
            "secret"
        );
    }

    private void authenticate(ProxyRouting routing) {
        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(routing));
    }

    private void stubCredential(ProxyRouting routing, LlmModelResolver.ProxyCredential credential) {
        when(
            resolver.resolveProxyCredential(
                eq(
                    new LlmModelResolver.ConnectionRef(
                        routing.connectionScope(),
                        routing.connectionId(),
                        routing.modelId(),
                        routing.workspaceId()
                    )
                )
            )
        ).thenReturn(credential);
    }
}
