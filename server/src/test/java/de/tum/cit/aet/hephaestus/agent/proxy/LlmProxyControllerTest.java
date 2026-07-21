package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * #1368 slice 5: the proxy no longer takes a {@code {provider}} path segment — the connection is
 * identified from the authenticated {@link ProxyRouting} (a job token's frozen ConfigSnapshot, or a
 * mentor session token), and the live credential is re-resolved via {@link LlmModelResolver} on
 * every call.
 */
class LlmProxyControllerTest extends BaseUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private LlmModelResolver llmModelResolver;

    @Mock
    private EgressPolicy egressPolicy;

    private LlmProxyController controller;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new LlmProxyController(
            WebClient.create(),
            llmModelResolver,
            egressPolicy,
            OBJECT_MAPPER,
            meterRegistry
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(ProxyRouting routing) {
        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(routing));
    }

    private static ProxyRouting anthropicRouting() {
        return new ProxyRouting(
            "job:test",
            "anthropic-messages",
            "https://api.anthropic.com",
            FundingSource.INSTANCE,
            7L,
            null
        );
    }

    private void stubCredential(ProxyRouting routing, LlmModelResolver.ProxyCredential credential) {
        when(
            llmModelResolver.resolveProxyCredential(
                eq(new LlmModelResolver.ConnectionRef(routing.connectionScope(), routing.connectionId())),
                eq(routing.legacyConfigId()),
                eq(routing.apiProtocol())
            )
        ).thenReturn(credential);
    }

    private void stubNoCredential(ProxyRouting routing) {
        stubCredential(routing, null);
    }

    @Nested
    class HeaderStripping {

        @Test
        void shouldStripXApiKey() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "x-api-key",
                "",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("x-api-key", "job-token-should-be-stripped");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.getFirst("x-api-key")).isEqualTo("sk-real-key");
            assertThat(out.get("x-api-key")).hasSize(1);
            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        void shouldStripAuthorization() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "Authorization",
                "Bearer ",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.AUTHORIZATION, "Bearer job-token-should-be-stripped");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-real-key");
            assertThat(out.get(HttpHeaders.AUTHORIZATION)).hasSize(1);
        }

        @Test
        void shouldStripAzureApiKey() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "api-key",
                "",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("api-key", "job-token-should-be-stripped");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.getFirst("api-key")).isEqualTo("sk-real-key");
        }

        @Test
        void shouldStripAllAuthHeaders() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "x-api-key",
                "",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("x-api-key", "token1");
            incoming.set(HttpHeaders.AUTHORIZATION, "Bearer token2");
            incoming.set("api-key", "token3");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.getFirst("x-api-key")).isEqualTo("sk-real-key");
            assertThat(out.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldRemoveHostAndSetAcceptEncodingIdentity() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "x-api-key",
                "",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.HOST, "app-server:8080");
            incoming.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.get(HttpHeaders.HOST)).isNull();
            assertThat(out.getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("identity");
        }

        @Test
        void shouldStripHopByHopHeaders() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "https://api.example.com",
                "x-api-key",
                "",
                null,
                "sk-real-key"
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.CONNECTION, "keep-alive");
            incoming.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            incoming.set("Keep-Alive", "timeout=5");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.get(HttpHeaders.CONNECTION)).isNull();
            assertThat(out.get(HttpHeaders.TRANSFER_ENCODING)).isNull();
            assertThat(out.get("Keep-Alive")).isNull();
            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("a keyless connection (null apiKey) forwards with auth headers stripped, none injected")
        void shouldForwardWithoutAuthHeaderWhenKeyless() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "http://localhost:11434",
                "Authorization",
                "Bearer ",
                null,
                null
            );
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("x-api-key", "job-token-should-be-stripped");
            incoming.set(HttpHeaders.AUTHORIZATION, "Bearer job-token-should-be-stripped");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.get("x-api-key")).isNull();
            assertThat(out.get(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(out.get("api-key")).isNull();
            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("a keyless connection with a blank (non-null) apiKey also forwards without an auth header")
        void shouldForwardWithoutAuthHeaderWhenApiKeyBlank() {
            LlmModelResolver.ProxyCredential credential = new LlmModelResolver.ProxyCredential(
                "http://localhost:11434",
                "x-api-key",
                "",
                null,
                "   "
            );
            HttpHeaders incoming = new HttpHeaders();

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, credential);

            assertThat(out.get("x-api-key")).isNull();
        }
    }

    @Nested
    class ConnectionResolution {

        @Test
        @DisplayName(
            "no authenticated principal on the security context throws (JobTokenAuthenticationFilter contract)"
        )
        void unauthenticatedRequestFails() {
            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.proxy(request, response, new HttpHeaders(), "{}".getBytes())
            );
        }

        @Test
        @DisplayName(
            "a token whose routing resolves no live connection/credential is refused with 502, never forwarded"
        )
        void unresolvableConnectionIsRefused() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubNoCredential(routing); // connection disabled / deleted since the snapshot was frozen

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("No credential configured for this connection");
        }

        @Test
        @DisplayName("egress policy rejecting the frozen base URL refuses forwarding")
        void egressPolicyRejectionIsRefused() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );
            doThrow(new IllegalArgumentException("Provider host must be a public HTTPS URL"))
                .when(egressPolicy)
                .validate(routing.baseUrl());

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream target not permitted");
        }

        @Test
        @DisplayName(
            "egress + upstream routing use the LIVE credential's base URL, never the job's frozen " +
                "ProxyRouting#baseUrl() — a connection repointed to a new host after dispatch must not send " +
                "its rotated key to the stale old host (rotation split-brain)"
        )
        void usesLiveCredentialBaseUrlNotFrozenRoutingBaseUrl() {
            ProxyRouting routing = new ProxyRouting(
                "job:test",
                "anthropic-messages",
                "https://old-frozen-host.example.com", // frozen in the job's ConfigSnapshot at dispatch
                FundingSource.INSTANCE,
                7L,
                null
            );
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential(
                    "https://new-live-host.example.com", // the connection was repointed after dispatch
                    "x-api-key",
                    "",
                    null,
                    "sk-rotated-key"
                )
            );
            // Only the LIVE host is stubbed to reject — the frozen host is left as a default (no-op)
            // mock, so this only fails 502/"Upstream target not permitted" if the controller validated
            // the live credential's base URL, not routing.baseUrl().
            doThrow(new IllegalArgumentException("blocked"))
                .when(egressPolicy)
                .validate("https://new-live-host.example.com");

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream target not permitted");
        }

        @Test
        @DisplayName("a resolved connection with no api key is treated as keyless, not refused as \"no credential\"")
        void keylessConnectionIsNotRefusedAsMissingCredential() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("http://localhost:11434", "Authorization", "Bearer ", null, null)
            );

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getBody()).isNotEqualTo("No credential configured for this connection");
        }
    }

    @Nested
    class PathTraversal {

        @Test
        @DisplayName("should reject .. in subpath")
        void shouldRejectPathTraversal() {
            authenticate(anthropicRouting());

            var request = new MockHttpServletRequest("POST", "/internal/llm/../../../etc/passwd");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void shouldRejectDoubleDotInDecodedPath() {
            authenticate(anthropicRouting());

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/../../etc/passwd");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void shouldRejectPercentEncodedDots() {
            authenticate(anthropicRouting());

            var request = new MockHttpServletRequest("POST", "/internal/llm/%2e%2e/etc/passwd");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void shouldRejectDoubleEncodedDots() {
            authenticate(anthropicRouting());

            var request = new MockHttpServletRequest("POST", "/internal/llm/%252e%252e/etc/passwd");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void shouldRejectBackslash() {
            authenticate(anthropicRouting());

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1\\..\\etc\\passwd");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    class SsrfProtection {

        @Test
        void shouldBuildCorrectUrl() {
            String url = LlmProxyController.buildUpstreamUrl(
                "https://api.anthropic.com",
                "/v1/messages",
                "stream=true"
            );
            assertThat(url).isEqualTo("https://api.anthropic.com/v1/messages?stream=true");
        }

        @Test
        void shouldPreserveHostAndSchemeFromBaseUrl() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "/v1/messages", null);
            var uri = java.net.URI.create(url);
            assertThat(uri.getHost()).isEqualTo("api.anthropic.com");
            assertThat(uri.getScheme()).isEqualTo("https");
        }

        @Test
        void shouldBuildUrlWithNoQuery() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null);
            assertThat(url).isEqualTo("https://api.openai.com/v1/chat/completions");
            assertThat(url).doesNotContain("?");
        }

        @Test
        void shouldBuildUrlWithEmptySubpath() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "", null);
            assertThat(url).isEqualTo("https://api.anthropic.com");
        }

        @Test
        void shouldNotMisinterpretAtInPathAsUserinfo() {
            String url = LlmProxyController.buildUpstreamUrl(
                "https://api.anthropic.com",
                "/user@evil.com/v1/messages",
                null
            );
            var uri = java.net.URI.create(url);
            assertThat(uri.getUserInfo()).as("@ in path must not create userinfo").isNull();
            assertThat(uri.getHost()).isEqualTo("api.anthropic.com");
            assertThat(uri.getScheme()).isEqualTo("https");
        }

        @Test
        void shouldRejectSsrfHostMismatchViaAtInjection() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );

            // Craft path where stripping "/internal/llm" leaves "@evil.com/v1/messages".
            // buildUpstreamUrl produces: "https://api.anthropic.com@evil.com/v1/messages"
            // URI.create parses this as: userInfo=api.anthropic.com, host=evil.com
            var request = new MockHttpServletRequest("POST", "/internal/llm@evil.com/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            assertThat(result.getBody()).isEqualTo("Invalid upstream target");
        }

        @Test
        void shouldHandleMalformedUri() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages with spaces");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    class Metrics {

        @Test
        void shouldRecordTimerWithApiProtocolTag() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubNoCredential(routing);

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            var timer = meterRegistry.find("llm.proxy.duration").tag("apiProtocol", "anthropic-messages").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        void shouldIncrementErrorCounterMultipleTimes() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubNoCredential(routing);

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());
            controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());
            controller.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            var errorCounter = meterRegistry
                .find("llm.proxy.errors")
                .tag("apiProtocol", "anthropic-messages")
                .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    class BodySizeValidation {

        @Test
        void shouldRejectOversizedBody() {
            // Body-size validation is a cheap syntactic check that runs BEFORE credential resolution
            // — no stub needed here (a stub would be flagged as unnecessary).
            authenticate(anthropicRouting());

            byte[] oversizedBody = new byte[4 * 1024 * 1024 + 1]; // 4MB + 1 byte
            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), oversizedBody);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(413);
        }

        @Test
        void shouldAcceptNullBody() {
            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );

            var request = new MockHttpServletRequest("GET", "/internal/llm/v1/models");
            var response = new MockHttpServletResponse();

            var result = controller.proxy(request, response, new HttpHeaders(), null);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isNotEqualTo(413);
        }
    }

    @Nested
    class UpstreamErrors {

        @Test
        void shouldReturn502OnWebClientRequestException() {
            @SuppressWarnings("unchecked")
            WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
            @SuppressWarnings("unchecked")
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            @SuppressWarnings("unchecked")
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient mockWebClient = mock(WebClient.class);

            when(mockWebClient.method(any(HttpMethod.class))).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(any(URI.class))).thenReturn(bodySpec);
            when(bodySpec.headers(any())).thenReturn(bodySpec);
            doReturn(headersSpec).when(bodySpec).bodyValue(any());
            when(headersSpec.exchangeToMono(any())).thenReturn(
                Mono.error(
                    new WebClientRequestException(
                        new java.net.ConnectException("Connection refused"),
                        HttpMethod.POST,
                        URI.create("https://api.anthropic.com/v1/messages"),
                        new HttpHeaders()
                    )
                )
            );

            var mockedController = new LlmProxyController(
                mockWebClient,
                llmModelResolver,
                egressPolicy,
                OBJECT_MAPPER,
                meterRegistry
            );

            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = mockedController.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream provider unreachable");

            var errorCounter = meterRegistry
                .find("llm.proxy.errors")
                .tag("apiProtocol", "anthropic-messages")
                .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }

        @Test
        void shouldReturn502OnGenericException() {
            @SuppressWarnings("unchecked")
            WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
            @SuppressWarnings("unchecked")
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            @SuppressWarnings("unchecked")
            WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
            WebClient mockWebClient = mock(WebClient.class);

            when(mockWebClient.method(any(HttpMethod.class))).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(any(URI.class))).thenReturn(bodySpec);
            when(bodySpec.headers(any())).thenReturn(bodySpec);
            doReturn(headersSpec).when(bodySpec).bodyValue(any());
            when(headersSpec.exchangeToMono(any())).thenReturn(
                Mono.error(new RuntimeException("Unexpected upstream error"))
            );

            var mockedController = new LlmProxyController(
                mockWebClient,
                llmModelResolver,
                egressPolicy,
                OBJECT_MAPPER,
                meterRegistry
            );

            ProxyRouting routing = anthropicRouting();
            authenticate(routing);
            stubCredential(
                routing,
                new LlmModelResolver.ProxyCredential("https://api.anthropic.com", "x-api-key", "", null, "sk-real-key")
            );

            var request = new MockHttpServletRequest("POST", "/internal/llm/v1/messages");
            var response = new MockHttpServletResponse();

            var result = mockedController.proxy(request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream request failed");
        }
    }

    @Nested
    class AzureBodySanitization {

        @Test
        void shouldStripReasoningSummaryForAzure() {
            byte[] body = "{\"model\":\"gpt-4\",\"reasoningSummary\":\"auto\",\"messages\":[]}".getBytes(
                StandardCharsets.UTF_8
            );
            byte[] sanitized = controller.sanitizeBodyForAzure(body);

            var tree = OBJECT_MAPPER.readTree(sanitized);
            assertThat(tree.has("reasoningSummary")).isFalse();
            assertThat(tree.has("model")).isTrue();
            assertThat(tree.has("messages")).isTrue();
        }

        @Test
        void shouldPassThroughNullBody() {
            assertThat(controller.sanitizeBodyForAzure(null)).isNull();
        }

        @Test
        void shouldPassThroughEmptyBody() {
            byte[] empty = new byte[0];
            assertThat(controller.sanitizeBodyForAzure(empty)).isSameAs(empty);
        }

        @Test
        void shouldPassThroughNonJsonBody() {
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            byte[] result = controller.sanitizeBodyForAzure(body);
            assertThat(result).isSameAs(body);
        }

        @Test
        void shouldRenameMaxTokensForAzure() {
            byte[] body = "{\"model\":\"gpt-4\",\"max_tokens\":1024,\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
            byte[] sanitized = controller.sanitizeBodyForAzure(body);

            var tree = OBJECT_MAPPER.readTree(sanitized);
            assertThat(tree.has("max_tokens")).isFalse();
            assertThat(tree.get("max_completion_tokens").asInt()).isEqualTo(1024);
            assertThat(tree.has("model")).isTrue();
        }

        @Test
        void shouldNotModifyCleanBody() {
            byte[] body = "{\"model\":\"gpt-4\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
            byte[] result = controller.sanitizeBodyForAzure(body);
            assertThat(result).isSameAs(body);
        }
    }

    @Nested
    class StreamUsageInjection {

        @Test
        @DisplayName("openai-completions streaming requests get stream_options.include_usage=true")
        void injectsIncludeUsageForStreamingOpenAiRequests() {
            byte[] body = "{\"model\":\"gpt-4\",\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
            byte[] result = controller.injectStreamUsageOption(body);

            var tree = OBJECT_MAPPER.readTree(result);
            assertThat(tree.path("stream_options").path("include_usage").asBoolean()).isTrue();
        }

        @Test
        void preservesExistingStreamOptions() {
            byte[] body = "{\"stream\":true,\"stream_options\":{\"foo\":1}}".getBytes(StandardCharsets.UTF_8);
            byte[] result = controller.injectStreamUsageOption(body);

            var tree = OBJECT_MAPPER.readTree(result);
            assertThat(tree.path("stream_options").path("foo").asInt()).isEqualTo(1);
            assertThat(tree.path("stream_options").path("include_usage").asBoolean()).isTrue();
        }

        @Test
        void doesNotTouchNonStreamingRequests() {
            byte[] body = "{\"model\":\"gpt-4\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
            byte[] result = controller.injectStreamUsageOption(body);
            assertThat(result).isSameAs(body);
        }

        @Test
        void passesThroughNullOrEmptyBody() {
            assertThat(controller.injectStreamUsageOption(null)).isNull();
            byte[] empty = new byte[0];
            assertThat(controller.injectStreamUsageOption(empty)).isSameAs(empty);
        }
    }

    @Nested
    class ProviderRouting {

        @Test
        void shouldUseAnthropicUpstream() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "/v1/messages", null);
            assertThat(java.net.URI.create(url).getHost()).isEqualTo("api.anthropic.com");
        }

        @Test
        void shouldUseOpenAIUpstream() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null);
            assertThat(java.net.URI.create(url).getHost()).isEqualTo("api.openai.com");
        }

        @Test
        @DisplayName(
            "auth header shape follows the LIVE connection's authHeaderName/authValuePrefix, not the api protocol"
        )
        void shouldInjectCorrectAuthPerConnection() {
            LlmModelResolver.ProxyCredential anthropicCredential = new LlmModelResolver.ProxyCredential(
                "https://api.anthropic.com",
                "x-api-key",
                "",
                null,
                "sk-ant"
            );
            LlmModelResolver.ProxyCredential openaiCredential = new LlmModelResolver.ProxyCredential(
                "https://api.openai.com",
                "Authorization",
                "Bearer ",
                null,
                "sk-oai"
            );
            HttpHeaders incoming = new HttpHeaders();

            HttpHeaders anthropicOut = controller.buildUpstreamHeaders(incoming, anthropicCredential);
            HttpHeaders openaiOut = controller.buildUpstreamHeaders(incoming, openaiCredential);

            assertThat(anthropicOut.getFirst("x-api-key")).isEqualTo("sk-ant");
            assertThat(anthropicOut.get(HttpHeaders.AUTHORIZATION)).isNull();

            assertThat(openaiOut.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-oai");
            assertThat(openaiOut.get("x-api-key")).isNull();
        }
    }
}
