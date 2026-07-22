package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    private LlmProxyController controller;

    @BeforeEach
    void setUp() {
        controller = new LlmProxyController(
            WebClient.create(),
            resolver,
            egressPolicy,
            OBJECT_MAPPER,
            new SimpleMeterRegistry()
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
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

            byte[] output = controller.prepareBody(input, "catalog-model", false);

            var tree = OBJECT_MAPPER.readTree(output);
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

            byte[] output = controller.prepareBody(input, "catalog-model", true);

            var tree = OBJECT_MAPPER.readTree(output);
            assertThat(tree.path("model").asString()).isEqualTo("catalog-model");
            assertThat(tree.path("stream_options").path("include_usage").asBoolean()).isTrue();
        }

        @Test
        void shouldAllowFunctionAndCustomTools() {
            byte[] input = "{\"tools\":[{\"type\":\"function\"},{\"type\":\"custom\"}]}".getBytes(
                StandardCharsets.UTF_8
            );

            assertThat(controller.prepareBody(input, "model", false)).isNotNull();
        }

        @Test
        void shouldRejectProviderHostedTools() {
            byte[] input = "{\"tools\":[{\"type\":\"web_search_preview\"}]}".getBytes(StandardCharsets.UTF_8);

            assertThat(controller.prepareBody(input, "model", false)).isNull();
        }

        @Test
        void shouldRejectHostedSearchOutsideTools() {
            byte[] input = "{\"web_search_options\":{}}".getBytes(StandardCharsets.UTF_8);

            assertThat(controller.prepareBody(input, "model", false)).isNull();
        }

        @Test
        void shouldRejectAudioOutput() {
            byte[] input = "{\"modalities\":[\"text\",\"audio\"],\"audio\":{}}".getBytes(StandardCharsets.UTF_8);

            assertThat(controller.prepareBody(input, "model", false)).isNull();
        }

        @Test
        void shouldAllowExplicitTextOnlyModality() {
            byte[] input = "{\"modalities\":[\"text\"]}".getBytes(StandardCharsets.UTF_8);

            assertThat(controller.prepareBody(input, "model", false)).isNotNull();
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

    private static ProxyRouting routing(String protocol) {
        return new ProxyRouting(
            "job:test",
            protocol,
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
                ),
                eq(routing.legacyConfigId()),
                eq(routing.apiProtocol())
            )
        ).thenReturn(credential);
    }
}
