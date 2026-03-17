package de.tum.in.www1.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@DisplayName("LlmProxyController")
class LlmProxyControllerTest extends BaseUnitTest {

    private static final LlmProxyProperties DEFAULT_PROPS = new LlmProxyProperties(
        "https://api.anthropic.com",
        "https://api.openai.com",
        "Authorization",
        true
    );

    private LlmProxyController controller;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new LlmProxyController(WebClient.create(), DEFAULT_PROPS, meterRegistry);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpAuthentication(AgentJob job) {
        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(job));
    }

    private AgentJob createJobWithApiKey(String apiKey) {
        var job = mock(AgentJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getLlmApiKey()).thenReturn(apiKey);
        return job;
    }

    @Nested
    @DisplayName("Header stripping — credential isolation")
    class HeaderStripping {

        @Test
        @DisplayName("should strip x-api-key (Anthropic job token) from upstream headers")
        void shouldStripXApiKey() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("x-api-key", "job-token-should-be-stripped");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.getFirst("x-api-key")).isEqualTo("sk-real-key");
            assertThat(out.get("x-api-key")).hasSize(1);
            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should strip Authorization header (OpenAI job token) from upstream headers")
        void shouldStripAuthorization() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.AUTHORIZATION, "Bearer job-token-should-be-stripped");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-real-key");
            assertThat(out.get(HttpHeaders.AUTHORIZATION)).hasSize(1);
        }

        @Test
        @DisplayName("should strip api-key header (Azure job token) from upstream headers")
        void shouldStripAzureApiKey() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("api-key", "job-token-should-be-stripped");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.get("api-key")).isNull();
        }

        @Test
        @DisplayName("should strip ALL auth headers even when multiple are present")
        void shouldStripAllAuthHeaders() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("x-api-key", "token1");
            incoming.set(HttpHeaders.AUTHORIZATION, "Bearer token2");
            incoming.set("api-key", "token3");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.getFirst("x-api-key")).isEqualTo("sk-real-key");
            assertThat(out.get(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(out.get("api-key")).isNull();
        }

        @Test
        @DisplayName("should remove Host and set Accept-Encoding to identity")
        void shouldRemoveHostAndSetAcceptEncodingIdentity() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.HOST, "app-server:8080");
            incoming.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.get(HttpHeaders.HOST)).isNull();
            assertThat(out.getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("identity");
        }

        @Test
        @DisplayName("should strip hop-by-hop headers")
        void shouldStripHopByHopHeaders() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set(HttpHeaders.CONNECTION, "keep-alive");
            incoming.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            incoming.set("Keep-Alive", "timeout=5");
            incoming.set("Content-Type", "application/json");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.get(HttpHeaders.CONNECTION)).isNull();
            assertThat(out.get(HttpHeaders.TRANSFER_ENCODING)).isNull();
            assertThat(out.get("Keep-Alive")).isNull();
            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should preserve non-auth custom headers")
        void shouldPreserveCustomHeaders() {
            var config = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();
            incoming.set("Content-Type", "application/json");
            incoming.set("anthropic-version", "2024-01-01");
            incoming.set("X-Custom", "preserved");

            HttpHeaders out = controller.buildUpstreamHeaders(incoming, config, "sk-real-key");

            assertThat(out.getFirst("Content-Type")).isEqualTo("application/json");
            assertThat(out.getFirst("anthropic-version")).isEqualTo("2024-01-01");
            assertThat(out.getFirst("X-Custom")).isEqualTo("preserved");
        }
    }

    @Nested
    @DisplayName("Path traversal rejection")
    class PathTraversal {

        @Test
        @DisplayName("should reject .. in subpath")
        void shouldRejectPathTraversal() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/../../../etc/passwd");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "token");

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("should reject double-dot even when URL-encoded in path")
        void shouldRejectDoubleDotInDecodedPath() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/../../etc/passwd");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "token");

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("should reject percent-encoded path traversal (%2e)")
        void shouldRejectPercentEncodedDots() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/%2e%2e/etc/passwd");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "token");

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("should reject double-encoded path traversal (%252e)")
        void shouldRejectDoubleEncodedDots() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/%252e%252e/etc/passwd");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "token");

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("should reject backslash in path")
        void shouldRejectBackslash() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1\\..\\etc\\passwd");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", "token");

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Null/blank API key handling")
    class NullApiKey {

        @Test
        @DisplayName("should return 502 when job has null API key")
        void shouldReturn502ForNullApiKey() {
            AgentJob job = createJobWithApiKey(null);
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        @DisplayName("should return 502 when job has blank API key")
        void shouldReturn502ForBlankApiKey() {
            AgentJob job = createJobWithApiKey("   ");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        @DisplayName("should increment error counter with provider tag for null API key")
        void shouldIncrementErrorCounterForNullApiKey() {
            AgentJob job = createJobWithApiKey(null);
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            var errorCounter = meterRegistry.find("llm.proxy.errors").tag("provider", "ANTHROPIC").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("SSRF host validation")
    class SsrfProtection {

        @Test
        @DisplayName("buildUpstreamUrl should construct correct URL")
        void shouldBuildCorrectUrl() {
            String url = LlmProxyController.buildUpstreamUrl(
                "https://api.anthropic.com",
                "/v1/messages",
                "stream=true"
            );
            assertThat(url).isEqualTo("https://api.anthropic.com/v1/messages?stream=true");
        }

        @Test
        @DisplayName("buildUpstreamUrl preserves host and scheme from base URL")
        void shouldPreserveHostAndSchemeFromBaseUrl() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "/v1/messages", null);
            var uri = java.net.URI.create(url);
            assertThat(uri.getHost()).isEqualTo("api.anthropic.com");
            assertThat(uri.getScheme()).isEqualTo("https");
        }

        @Test
        @DisplayName("buildUpstreamUrl with no query params")
        void shouldBuildUrlWithNoQuery() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null);
            assertThat(url).isEqualTo("https://api.openai.com/v1/chat/completions");
            assertThat(url).doesNotContain("?");
        }

        @Test
        @DisplayName("buildUpstreamUrl with empty subpath")
        void shouldBuildUrlWithEmptySubpath() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "", null);
            assertThat(url).isEqualTo("https://api.anthropic.com");
        }

        @Test
        @DisplayName("@ in path should not be misinterpreted as userinfo")
        void shouldNotMisinterpretAtInPathAsUserinfo() {
            // An @ in the URL path (after the authority) is a harmless literal character,
            // not a userinfo separator. Verify the constructed URI parses correctly.
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
        @DisplayName("SSRF defense detects userinfo in authority (defense-in-depth)")
        void shouldDetectUserInfoInAuthority() {
            // The controller's SSRF check rejects URIs with userinfo.
            // This verifies the defense works at the URI level — in production,
            // buildUpstreamUrl cannot produce userinfo since the base URL is from config.
            var uri = java.net.URI.create("https://attacker:pass@evil.com/v1/messages");
            assertThat(uri.getUserInfo()).as("URI with @ in authority has userinfo").isNotNull();
            assertThat(uri.getHost()).isNotEqualTo("api.anthropic.com");
        }

        @Test
        @DisplayName("should reject SSRF when subpath hijacks URI authority via @ injection")
        void shouldRejectSsrfHostMismatchViaAtInjection() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            // Craft path where stripping "/internal/llm/anthropic" leaves "@evil.com/v1/messages".
            // buildUpstreamUrl produces: "https://api.anthropic.com@evil.com/v1/messages"
            // URI.create parses this as: userInfo=api.anthropic.com, host=evil.com
            // The SSRF check catches both userInfo != null AND host != expected host.
            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic@evil.com/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy("anthropic", request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            assertThat(result.getBody()).isEqualTo("Invalid upstream target");
        }

        @Test
        @DisplayName("should handle malformed URI gracefully (return 400 not 500)")
        void shouldHandleMalformedUri() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            // Path with characters invalid for URI
            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages with spaces");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            var result = controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            // Should return 400, not 500 (IllegalArgumentException caught)
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Metrics and observability")
    class Metrics {

        @Test
        @DisplayName("should record timer with provider tag")
        void shouldRecordTimerWithProviderTag() {
            AgentJob job = createJobWithApiKey(null);
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            var timer = meterRegistry.find("llm.proxy.duration").tag("provider", "ANTHROPIC").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record timer even when proxy fails early")
        void shouldRecordTimerOnEarlyReturn() {
            AgentJob job = createJobWithApiKey("   ");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            var timer = meterRegistry.find("llm.proxy.duration").tag("provider", "ANTHROPIC").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
        }

        @Test
        @DisplayName("should increment error counter with provider tag on consecutive failures")
        void shouldIncrementErrorCounterMultipleTimes() {
            AgentJob job = createJobWithApiKey(null);
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            controller.proxy("anthropic", request, response, headers, "{}".getBytes());
            controller.proxy("anthropic", request, response, headers, "{}".getBytes());
            controller.proxy("anthropic", request, response, headers, "{}".getBytes());

            var errorCounter = meterRegistry.find("llm.proxy.errors").tag("provider", "ANTHROPIC").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("should track errors per provider independently")
        void shouldTrackErrorsPerProvider() {
            AgentJob job = createJobWithApiKey(null);
            setUpAuthentication(job);

            var anthropicReq = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var openaiReq = new MockHttpServletRequest("POST", "/internal/llm/openai/v1/chat/completions");
            var response = new MockHttpServletResponse();
            HttpHeaders headers = new HttpHeaders();

            controller.proxy("anthropic", anthropicReq, response, headers, "{}".getBytes());
            controller.proxy("openai", openaiReq, response, headers, "{}".getBytes());
            controller.proxy("openai", openaiReq, response, headers, "{}".getBytes());

            var anthropicErrors = meterRegistry.find("llm.proxy.errors").tag("provider", "ANTHROPIC").counter();
            var openaiErrors = meterRegistry.find("llm.proxy.errors").tag("provider", "OPENAI").counter();

            assertThat(anthropicErrors).isNotNull();
            assertThat(anthropicErrors.count()).isEqualTo(1.0);
            assertThat(openaiErrors).isNotNull();
            assertThat(openaiErrors.count()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("Request body size validation")
    class BodySizeValidation {

        @Test
        @DisplayName("should reject request body exceeding 4MB limit")
        void shouldRejectOversizedBody() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            byte[] oversizedBody = new byte[4 * 1024 * 1024 + 1]; // 4MB + 1 byte
            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy("anthropic", request, response, new HttpHeaders(), oversizedBody);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(413);
        }

        @Test
        @DisplayName("should accept request body at exactly 4MB limit")
        void shouldAcceptBodyAtLimit() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            byte[] maxBody = new byte[4 * 1024 * 1024]; // exactly 4MB
            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();

            var result = controller.proxy("anthropic", request, response, new HttpHeaders(), maxBody);

            // Should pass body validation (may get 502 from WebClient, but not 413)
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isNotEqualTo(413);
        }

        @Test
        @DisplayName("should accept null body (GET request)")
        void shouldAcceptNullBody() {
            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("GET", "/internal/llm/anthropic/v1/models");
            var response = new MockHttpServletResponse();

            var result = controller.proxy("anthropic", request, response, new HttpHeaders(), null);

            // Should not fail body validation
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isNotEqualTo(413);
        }
    }

    @Nested
    @DisplayName("Upstream error handling")
    class UpstreamErrors {

        @Test
        @DisplayName("should return 502 and increment errors on WebClientRequestException")
        void shouldReturn502OnWebClientRequestException() {
            // Mock WebClient to throw WebClientRequestException (upstream unreachable)
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

            var mockedController = new LlmProxyController(mockWebClient, DEFAULT_PROPS, meterRegistry);

            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();

            var result = mockedController.proxy("anthropic", request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream provider unreachable");

            var errorCounter = meterRegistry.find("llm.proxy.errors").tag("provider", "ANTHROPIC").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should return 502 and increment errors on generic upstream exception")
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

            var mockedController = new LlmProxyController(mockWebClient, DEFAULT_PROPS, meterRegistry);

            AgentJob job = createJobWithApiKey("sk-real-key");
            setUpAuthentication(job);

            var request = new MockHttpServletRequest("POST", "/internal/llm/anthropic/v1/messages");
            var response = new MockHttpServletResponse();

            var result = mockedController.proxy("anthropic", request, response, new HttpHeaders(), "{}".getBytes());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
            assertThat(result.getBody()).isEqualTo("Upstream request failed");

            var errorCounter = meterRegistry.find("llm.proxy.errors").tag("provider", "ANTHROPIC").counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Provider routing")
    class ProviderRouting {

        @Test
        @DisplayName("should use Anthropic upstream URL for anthropic provider")
        void shouldUseAnthropicUpstream() {
            // Verify buildUpstreamUrl produces correct Anthropic URL
            String url = LlmProxyController.buildUpstreamUrl("https://api.anthropic.com", "/v1/messages", null);
            assertThat(java.net.URI.create(url).getHost()).isEqualTo("api.anthropic.com");
        }

        @Test
        @DisplayName("should use OpenAI upstream URL for openai provider")
        void shouldUseOpenAIUpstream() {
            String url = LlmProxyController.buildUpstreamUrl("https://api.openai.com", "/v1/chat/completions", null);
            assertThat(java.net.URI.create(url).getHost()).isEqualTo("api.openai.com");
        }

        @Test
        @DisplayName("should inject x-api-key for Anthropic, Bearer for OpenAI")
        void shouldInjectCorrectAuthPerProvider() {
            var anthropicConfig = ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, DEFAULT_PROPS);
            var openaiConfig = ProviderProxyConfig.forProvider(LlmProvider.OPENAI, DEFAULT_PROPS);
            HttpHeaders incoming = new HttpHeaders();

            HttpHeaders anthropicOut = controller.buildUpstreamHeaders(incoming, anthropicConfig, "sk-ant");
            HttpHeaders openaiOut = controller.buildUpstreamHeaders(incoming, openaiConfig, "sk-oai");

            assertThat(anthropicOut.getFirst("x-api-key")).isEqualTo("sk-ant");
            assertThat(anthropicOut.get(HttpHeaders.AUTHORIZATION)).isNull();

            assertThat(openaiOut.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-oai");
            assertThat(openaiOut.get("x-api-key")).isNull();
        }
    }
}
