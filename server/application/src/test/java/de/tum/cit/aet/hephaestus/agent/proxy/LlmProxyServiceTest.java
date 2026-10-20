package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.LlmProperties;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class LlmProxyServiceTest extends BaseUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private LlmModelResolver resolver;

    @Mock
    private ProxyRequestPolicy requestPolicy;

    @Mock
    private ProxyBudgetGate budgetGate;

    @Mock
    private ProxyUsageAccumulator usageAccumulator;

    @Mock
    private MentorTurnUsageAccumulator mentorTurnUsageAccumulator;

    @Mock
    private ProxyRoutingService routingService;

    private LlmProxyService controller;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        lenient().when(requestPolicy.allows(any())).thenReturn(true);
        meterRegistry = new SimpleMeterRegistry();
        controller = new LlmProxyService(
                Tracer.NOOP,
                WebClient.create(),
                resolver,
                OBJECT_MAPPER,
                new ProxyAccounting(
                        budgetGate, usageAccumulator, mentorTurnUsageAccumulator, meterRegistry, OBJECT_MAPPER),
                requestPolicy,
                mock(ScanCallGuard.class),
                routingService);
    }

    @Test
    void shouldRefuseNoAiBeforeCredentialsOrNetworkAccess() {
        var routing = routing("openai-completions");
        authenticate(routing);
        when(requestPolicy.allows(routing)).thenReturn(false);
        var result = controller.proxy(
                request("POST", "/internal/llm/chat/completions"),
                new MockHttpServletResponse(),
                new HttpHeaders(),
                jsonBody());
        assertThat(Objects.requireNonNull(result).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(resolver, budgetGate);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class BudgetGate {

        @Test
        void the429BodyNamesTheSharedPurseForASharedModelCall() {
            var routing = routing("openai-completions");
            authenticate(routing);
            when(budgetGate.isBlocked(routing)).thenReturn(true);

            var result = controller.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    new MockHttpServletResponse(),
                    new HttpHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(429);
            assertThat(String.valueOf(result.getBody()))
                    .contains("Shared-model budget reached")
                    .contains("raises the budget");
            assertThat(blocked("openai-completions", "instance")).isEqualTo(1);
            assertThat(blocked("openai-completions", "byo")).isZero();
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
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(429);
            assertThat(String.valueOf(result.getBody()))
                    .contains("Own-provider budget reached")
                    .contains("raises the cap");
            assertThat(blocked("openai-completions", "byo")).isEqualTo(1);
            assertThat(blocked("openai-completions", "instance")).isZero();
        }

        /** 502, not 429: the credential resolves to nothing, so anything but 429 proves the call got through. */
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
                    jsonBody());

            assertThat(result).isNotNull();
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
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(Objects.requireNonNull(result).getStatusCode().value()).isEqualTo(403);
            verifyNoInteractions(resolver);
            verifyNoInteractions(mentorTurnUsageAccumulator);
            verifyNoInteractions(usageAccumulator);
        }

        @Test
        void refusesBeforeConsultingTheBudgetGate() {
            authenticate(unboundMentorSessionRouting());

            controller.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    new MockHttpServletResponse(),
                    new HttpHeaders(),
                    jsonBody());

            verifyNoInteractions(budgetGate);
        }
    }

    @Nested
    class SafeSurface {

        /** A truncated runner or a retried request whose body was already consumed sends no bytes. */
        @Test
        void shouldRejectABodylessCallBeforeCredentialResolution() {
            authenticate(routing("openai-responses"));

            var result = controller.proxy(
                    request("POST", "/internal/llm/responses"), new MockHttpServletResponse(), new HttpHeaders(), null);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectNonPostBeforeCredentialResolution() {
            authenticate(routing("openai-completions"));
            var request = request("GET", "/internal/llm/chat/completions");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(405);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectWrongPathBeforeCredentialResolution() {
            authenticate(routing("openai-completions"));
            var request = request("POST", "/internal/llm/models");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectQueryBeforeCredentialResolution() {
            authenticate(routing("openai-responses"));
            var request = request("POST", "/internal/llm/responses");
            request.setQueryString("api-version=unsafe");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectProtocolPathMismatchBeforeCredentialResolution() {
            authenticate(routing("openai-responses"));
            var request = request("POST", "/internal/llm/chat/completions");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRejectANonChatProtocolOnTheReviewPathsWhenTheRoutingNamesOne() {
            authenticate(routing("openai-embeddings"));
            var request = request("POST", "/internal/llm/embeddings");

            var result = controller.proxy(request, new MockHttpServletResponse(), new HttpHeaders(), jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldAcceptChatCompletionsPath() {
            var routing = routing("openai-completions");
            authenticate(routing);
            stubCredential(routing, credential("openai-completions", LlmAuthMode.BEARER));
            doThrow(new IllegalArgumentException("blocked"))
                    .when(requestPolicy)
                    .validateTarget("https://api.example.com/v1");

            var result = controller.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    new MockHttpServletResponse(),
                    new HttpHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        void shouldAcceptResponsesPath() {
            var routing = routing("openai-responses");
            authenticate(routing);
            stubCredential(routing, credential("openai-responses", LlmAuthMode.API_KEY));
            doThrow(new IllegalArgumentException("blocked"))
                    .when(requestPolicy)
                    .validateTarget("https://api.example.com/v1");

            var result = controller.proxy(
                    request("POST", "/internal/llm/responses"),
                    new MockHttpServletResponse(),
                    new HttpHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
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
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
            verify(requestPolicy, never()).validateTarget(any());
        }
    }

    @Nested
    class BodyLocking {

        @Test
        void shouldForceAuthoritativeModel() {
            byte[] input = "{\"model\":\"runner-controlled\",\"service_tier\":\"priority\",\"messages\":[]}"
                    .getBytes(StandardCharsets.UTF_8);

            var prepared = controller.prepareBody(input, "catalog-model", false);
            assertThat(prepared).isNotNull();

            assertThat(prepared.body()).isNotNull();
            var tree = OBJECT_MAPPER.readTree(prepared.body());
            assertThat(tree.path("model").asString()).isEqualTo("catalog-model");
            assertThat(tree.has("service_tier")).isFalse();
        }

        @Test
        void shouldRejectMalformedJson() {
            assertThat(controller.prepareBody("not-json".getBytes(StandardCharsets.UTF_8), "model", false))
                    .isNull();
        }

        @Test
        void shouldRejectJsonThatIsNotAnObject() {
            assertThat(controller.prepareBody("[]".getBytes(StandardCharsets.UTF_8), "model", false))
                    .isNull();
        }

        @Test
        void shouldAddStreamingUsageForChatCompletions() {
            byte[] input = "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8);

            var prepared = controller.prepareBody(input, "catalog-model", true);
            assertThat(prepared).isNotNull();

            assertThat(prepared.body()).isNotNull();
            var tree = OBJECT_MAPPER.readTree(prepared.body());
            assertThat(tree.path("model").asString()).isEqualTo("catalog-model");
            assertThat(tree.path("stream_options").path("include_usage").asBoolean())
                    .isTrue();
            // The un-augmented body is kept so a provider that rejects the flag can be retried on
            // exactly what the caller sent.
            assertThat(OBJECT_MAPPER.readTree(prepared.withoutUsageRequest()).has("stream_options"))
                    .isFalse();
        }

        static Stream<Arguments> requestedCapabilities() {
            return Stream.of(
                    Arguments.of(
                            "{\"tools\":[{\"type\":\"function\"},{\"type\":\"custom\"}]}", true, "caller-run tools"),
                    Arguments.of("{\"tools\":[{\"type\":\"web_search_preview\"}]}", false, "a provider-hosted tool"),
                    Arguments.of("{\"web_search_options\":{}}", false, "hosted search asked for outside tools"),
                    Arguments.of("{\"modalities\":[\"text\",\"audio\"],\"audio\":{}}", false, "audio output"),
                    Arguments.of("{\"modalities\":[\"text\"]}", true, "an explicit text-only modality"));
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

    @Nested
    class PracticeScriptRoutes {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "Comment-Quality", "../comment-quality", "-comment-quality"})
        void shouldRefuseAndCountACallWhenItNamesNoValidPractice(@Nullable String practice) {
            authenticate(practiceScriptRouting(ModelKind.EMBEDDING, "openai-embeddings"));
            HttpHeaders headers = new HttpHeaders();
            if (practice != null) {
                headers.set(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PRACTICE_HEADER, practice);
            }

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/embedding/embeddings"),
                    new MockHttpServletResponse(),
                    headers,
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            assertThat(meterRegistry
                            .counter("llm.proxy.practice_script.unattributed")
                            .count())
                    .isEqualTo(1.0);
            verifyNoInteractions(resolver, usageAccumulator);
        }

        @Test
        void shouldRefuseACallWhenItsPracticeIsLongerThanASlug() {
            authenticate(practiceScriptRouting(ModelKind.EMBEDDING, "openai-embeddings"));

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/embedding/embeddings"),
                    new MockHttpServletResponse(),
                    practiceHeaders("a".repeat(65)),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void shouldRefuseAnOpWhenItIsNotThePathOfTheSlotsProtocol() {
            authenticate(practiceScriptRouting(ModelKind.DECISION, "openai-decisions"));

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/decision/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldAcceptChatCompletionsOnTheDecisionSlotWhenItsModelIsAChatCompletionsModel() {
            var routing = practiceScriptRouting(ModelKind.DECISION, "openai-completions");
            authenticate(routing);
            stubCredential(routing, credential("openai-completions", LlmAuthMode.BEARER));
            doThrow(new IllegalArgumentException("blocked"))
                    .when(requestPolicy)
                    .validateTarget("https://api.example.com/v1");

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/decision/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(502);
        }

        @Test
        void shouldRefuseASlotWhenItsFrozenProtocolServesAnotherKind() {
            authenticate(practiceScriptRouting(ModelKind.EMBEDDING, "openai-decisions"));

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/embedding/decisions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRefuseAStreamWhenAPracticeScriptCallAsksForOne() {
            authenticate(practiceScriptRouting(ModelKind.CHAT, "openai-completions"));

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/chat/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8));

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRefuseAPracticeScriptRoutingWhenItReachesAReviewPath() {
            authenticate(practiceScriptRouting(ModelKind.CHAT, "openai-completions"));

            var result = controller.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRefuseAReviewRoutingWhenItReachesAPracticeScriptPath() {
            authenticate(routing("openai-completions"));

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/chat/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            verifyNoInteractions(resolver);
        }

        @Test
        void shouldRefuseWithPaymentRequiredWhenTheDirectModelCapIsSpent() {
            var routing = practiceScriptRouting(ModelKind.EMBEDDING, "openai-embeddings");
            authenticate(routing);
            when(budgetGate.isDirectModelCapReached(Objects.requireNonNull(routing.attempt())))
                    .thenReturn(true);

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/embedding/embeddings"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(402);
            assertThat(String.valueOf(result.getBody())).isEqualTo("Practice script budget spent");
            verifyNoInteractions(resolver);
            assertThat(blocked("openai-embeddings", "practice_script_attempt")).isEqualTo(1);
            assertThat(blocked("openai-embeddings", "instance")).isZero();
        }

        @Test
        void shouldRefuseWithPaymentRequiredWhenTheMonthlyBudgetStopsAPracticeScriptCall() {
            var routing = practiceScriptRouting(ModelKind.CHAT, "openai-completions");
            authenticate(routing);
            when(budgetGate.isBlocked(routing)).thenReturn(true);

            var result = controller.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/chat/chat/completions"),
                    new MockHttpServletResponse(),
                    practiceHeaders(),
                    jsonBody());

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(402);
            assertThat(String.valueOf(result.getBody())).contains("Shared-model budget reached");
            verifyNoInteractions(resolver);
            // An admin fixes a monthly budget and an operator the attempt's cap, so an alert must tell them apart.
            assertThat(blocked("openai-completions", "instance")).isEqualTo(1);
            assertThat(blocked("openai-completions", "practice_script_attempt")).isZero();
        }

        @ParameterizedTest
        @CsvSource({
            "DECISION,openai-decisions,/internal/llm/practice-script/decision/decisions,questions,64",
            "EMBEDDING,openai-embeddings,/internal/llm/practice-script/embedding/embeddings,input,64",
            "RERANKING,cohere-rerank,/internal/llm/practice-script/reranking/rerank,documents,256"
        })
        void shouldRefuseACallBeforeTheUpstreamWhenItHoldsMoreEntriesThanItsBound(
                ModelKind slot, String protocol, String path, String field, int max) {
            authenticate(practiceScriptRouting(slot, protocol));

            var atBound = controller.proxyPracticeScript(
                    request("POST", path), new MockHttpServletResponse(), practiceHeaders(), entries(field, max));
            var aboveBound = controller.proxyPracticeScript(
                    request("POST", path), new MockHttpServletResponse(), practiceHeaders(), entries(field, max + 1));

            assertThat(atBound).isNotNull();
            assertThat(atBound.getStatusCode().value()).isEqualTo(502);
            assertThat(aboveBound).isNotNull();
            assertThat(aboveBound.getStatusCode().value()).isEqualTo(413);
            assertThat(String.valueOf(aboveBound.getBody()))
                    .isEqualTo("'" + field + "' must hold at most " + max + " entries");
        }

        private static HttpHeaders practiceHeaders() {
            return practiceHeaders("comment-quality");
        }

        private static HttpHeaders practiceHeaders(String practice) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PRACTICE_HEADER, practice);
            return headers;
        }

        private static byte[] entries(String field, int count) {
            var body = OBJECT_MAPPER.createObjectNode();
            body.set(field, OBJECT_MAPPER.valueToTree(Collections.nCopies(count, "entry")));
            return OBJECT_MAPPER.writeValueAsBytes(body);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLETED, 200, OK",
        "STREAM_FAILED, 200, ERROR",
        "TIMED_OUT, 200, ERROR",
        "CLIENT_DISCONNECTED, 200, ERROR",
        "HTTP_ERROR, 502, ERROR"
    })
    void shouldExportTheOperationOutcomeWithoutLeakingFailureDetails(
            String outcome, int status, StatusCode expectedStatus) throws Exception {
        var exporter = mock(SpanExporter.class);
        when(exporter.export(any())).thenReturn(CompletableResultCode.ofSuccess());
        when(exporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
        try (var provider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var tracer = new OtelTracer(provider.get("test"), new OtelCurrentTraceContext(), event -> {});
            var buffer =
                    DefaultDataBufferFactory.sharedInstance.wrap("data: response\n\n".getBytes(StandardCharsets.UTF_8));
            Flux<DataBuffer> body =
                    switch (outcome) {
                        case "STREAM_FAILED" -> Flux.error(new IOException("private-provider-token"));
                        // A typed timeout cause; the span keeps the closed outcome and never the words.
                        case "TIMED_OUT" -> Flux.error(new TimeoutException("private-provider-token"));
                        default -> Flux.just(buffer);
                    };
            var upstreamResponse = ClientResponse.create(HttpStatusCode.valueOf(status))
                    .header(HttpHeaders.CONTENT_TYPE, status == 200 ? "text/event-stream" : "application/json")
                    .body(body)
                    .build();
            var client = WebClient.builder()
                    .exchangeFunction(request -> Mono.just(upstreamResponse))
                    .build();
            var service = new LlmProxyService(
                    tracer,
                    client,
                    resolver,
                    OBJECT_MAPPER,
                    new ProxyAccounting(
                            budgetGate,
                            usageAccumulator,
                            mentorTurnUsageAccumulator,
                            new SimpleMeterRegistry(),
                            OBJECT_MAPPER),
                    requestPolicy,
                    mock(ScanCallGuard.class),
                    routingService);
            var routing = routing("openai-completions");
            authenticate(routing);
            when(resolver.resolveProxyCredential(any()))
                    .thenReturn(credential("openai-completions", LlmAuthMode.BEARER));
            var servletResponse = new MockHttpServletResponse();
            HttpServletResponse response = servletResponse;
            boolean clientDisconnected = outcome.equals("CLIENT_DISCONNECTED");
            if (clientDisconnected) {
                var disconnected = mock(HttpServletResponse.class);
                var output = mock(ServletOutputStream.class);
                when(disconnected.getOutputStream()).thenReturn(output);
                when(disconnected.getStatus()).thenReturn(200);
                doThrow(new IOException("private-client-detail")).when(output).write(any(byte[].class));
                response = disconnected;
            }
            var result = service.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    response,
                    new HttpHeaders(),
                    "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8));
            if (status == 200) {
                assertThat(result).isNull();
                if (!clientDisconnected) {
                    assertThat(servletResponse.getStatus()).isEqualTo(200);
                }
            } else {
                assertThat(result).isNotNull();
                assertThat(result.getStatusCode().value()).isEqualTo(status);
            }
            verify(exporter).export(argThat(spans -> {
                assertThat(spans).hasSize(1);
                var span = spans.iterator().next();
                assertThat(span.getStatus().getStatusCode()).isEqualTo(expectedStatus);
                var errorType = span.getAttributes().get(AttributeKey.stringKey("error.type"));
                if (expectedStatus == StatusCode.ERROR) {
                    assertThat(errorType).isEqualTo(status == 502 ? "502" : outcome);
                } else {
                    assertThat(errorType).isNull();
                }
                assertThat(span.getEvents().toString())
                        .doesNotContain("private-provider-token", "private-client-detail");
                return true;
            }));
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "DECISION|openai-decisions|decision/decisions|"
                        + "{\"answers\":[],\"usage\":{\"input_tokens\":40,\"output_tokens\":3}}|40|3|",
                "CHAT|openai-completions|chat/chat/completions|{\"usage\":{\"prompt_tokens\":100,"
                        + "\"completion_tokens\":5,\"prompt_tokens_details\":{\"cached_tokens\":60}}}|100|5|",
                "RERANKING|cohere-rerank|reranking/rerank|"
                        + "{\"results\":[],\"meta\":{\"billed_units\":{\"search_units\":3}}}|||3"
            })
    void shouldTagTheUsageOnTheSpanWhenAPracticeScriptCallIsNotStreamed(
            ModelKind slot,
            String protocol,
            String op,
            String reply,
            @Nullable Long inputTokens,
            @Nullable Long outputTokens,
            @Nullable Long searchUnits) {
        var exporter = mock(SpanExporter.class);
        when(exporter.export(any())).thenReturn(CompletableResultCode.ofSuccess());
        when(exporter.shutdown()).thenReturn(CompletableResultCode.ofSuccess());
        try (var provider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var upstreamResponse = ClientResponse.create(HttpStatusCode.valueOf(200))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(reply)
                    .build();
            var service = new LlmProxyService(
                    new OtelTracer(provider.get("test"), new OtelCurrentTraceContext(), event -> {}),
                    WebClient.builder()
                            .exchangeFunction(request -> Mono.just(upstreamResponse))
                            .build(),
                    resolver,
                    OBJECT_MAPPER,
                    new ProxyAccounting(
                            budgetGate,
                            usageAccumulator,
                            mentorTurnUsageAccumulator,
                            new SimpleMeterRegistry(),
                            OBJECT_MAPPER),
                    requestPolicy,
                    mock(ScanCallGuard.class),
                    routingService);
            authenticate(practiceScriptRouting(slot, protocol));
            when(resolver.resolveProxyCredential(any())).thenReturn(credential(protocol, LlmAuthMode.BEARER));
            HttpHeaders headers = new HttpHeaders();
            headers.set(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PRACTICE_HEADER, "comment-quality");

            var result = service.proxyPracticeScript(
                    request("POST", "/internal/llm/practice-script/" + op),
                    new MockHttpServletResponse(),
                    headers,
                    "{}".getBytes(StandardCharsets.UTF_8));

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            verify(exporter).export(argThat(spans -> {
                var attributes = spans.iterator().next().getAttributes();
                assertThat(attributes.get(AttributeKey.longKey("gen_ai.usage.input_tokens")))
                        .isEqualTo(inputTokens);
                assertThat(attributes.get(AttributeKey.longKey("gen_ai.usage.output_tokens")))
                        .isEqualTo(outputTokens);
                assertThat(attributes.get(AttributeKey.longKey("hephaestus.usage.search_units")))
                        .isEqualTo(searchUnits);
                return true;
            }));
        }
    }

    /**
     * These tests drive an actual HTTP stream because the failure they guard against is invisible to any
     * mock: a controller that returns the instant it sees an SSE body serves a perfect response and
     * reaches no accounting at all, so only the ledger tells the two apart.
     */
    @Nested
    class Streaming {

        private MockWebServer upstream;
        private LlmProxyService streamingController;
        private SimpleMeterRegistry streamingMeterRegistry;

        @BeforeEach
        void startUpstream() throws IOException {
            upstream = new MockWebServer();
            upstream.start();
            streamingMeterRegistry = new SimpleMeterRegistry();
            streamingController = controllerWith(WebClient.builder().build());
        }

        private LlmProxyService controllerWith(WebClient client) {
            return controllerWith(client, Tracer.NOOP);
        }

        private LlmProxyService controllerWith(WebClient client, Tracer tracer) {
            return new LlmProxyService(
                    tracer,
                    client,
                    resolver,
                    OBJECT_MAPPER,
                    new ProxyAccounting(
                            budgetGate,
                            usageAccumulator,
                            mentorTurnUsageAccumulator,
                            streamingMeterRegistry,
                            OBJECT_MAPPER),
                    requestPolicy,
                    mock(ScanCallGuard.class),
                    routingService);
        }

        @AfterEach
        void stopUpstream() throws IOException {
            upstream.close();
        }

        private ProxyRouting streamingRouting() {
            return streamingRouting(laterThanAnyTest());
        }

        private ProxyRouting streamingRouting(Instant workDeadline) {
            return new ProxyRouting(
                    "job:stream",
                    "openai-completions",
                    upstream.url("/v1").toString(),
                    UsageFundingSource.INSTANCE,
                    7L,
                    8L,
                    9L,
                    ATTEMPT,
                    null,
                    null,
                    workDeadline);
        }

        private MockHttpServletResponse proxyStream(String... sseFrames) {
            StringBuilder body = new StringBuilder();
            for (String frame : sseFrames) {
                body.append("data: ").append(frame).append("\n\n");
            }
            upstream.enqueue(new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(body.toString())
                    .build());
            ProxyRouting routing = streamingRouting();
            authenticate(routing);
            when(resolver.resolveProxyCredential(new LlmModelResolver.ConnectionRef(
                            routing.connectionScope(),
                            routing.connectionId(),
                            routing.modelId(),
                            routing.workspaceId())))
                    .thenReturn(new LlmModelResolver.ProxyCredential(
                            upstream.url("/v1").toString(),
                            "openai-completions",
                            LlmAuthMode.BEARER,
                            "catalog-model",
                            "secret"));
            MockHttpServletResponse response = new MockHttpServletResponse();
            controllerProxy(response);
            return response;
        }

        private void controllerProxy(MockHttpServletResponse response) {
            streamingController.proxy(
                    request("POST", "/internal/llm/chat/completions"),
                    response,
                    new HttpHeaders(),
                    "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("a streamed call is billed for the usage its final frame reports")
        void aStreamedCallIsBilled() throws Exception {
            MockHttpServletResponse response = proxyStream(
                    "{\"choices\":[{\"delta\":{\"content\":\"hi\"}}],\"usage\":null}",
                    "{\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":40,"
                            + "\"prompt_tokens_details\":{\"cached_tokens\":25}}}",
                    "[DONE]");

            assertThat(response.getContentAsString())
                    .contains("\"content\":\"hi\"")
                    .contains("[DONE]");
            ArgumentCaptor<ProxyTokenUsage> usage = ArgumentCaptor.forClass(ProxyTokenUsage.class);
            verify(usageAccumulator).accumulate(eq(ATTEMPT), isNull(), usage.capture());
            assertThat(usage.getValue().billableInputTokens()).isEqualTo(75);
            assertThat(usage.getValue().outputTokens()).isEqualTo(40);
            assertThat(usage.getValue().cacheReadTokens()).isEqualTo(25);
            assertThat(missingUsage()).isZero();
        }

        @Test
        @DisplayName("a stream that terminates before the usage frame records nothing and is counted as missing")
        void aTruncatedStreamRecordsNothing() throws Exception {
            MockHttpServletResponse response =
                    proxyStream("{\"choices\":[{\"delta\":{\"content\":\"cut off here\"}}],\"usage\":null}");

            assertThat(response.getContentAsString()).contains("cut off here");
            verifyNoInteractions(usageAccumulator);
            assertThat(missingUsage()).isEqualTo(1.0);
        }

        @Test
        void malformedStreamUsageIsCounted() throws Exception {
            proxyStream("{\"usage\":{\"prompt_tokens\":1,\"prompt_tokens_details\":{\"cached_tokens\":2}}}");

            assertThat(streamingMeterRegistry
                            .counter("llm.proxy.usage.unparseable", "sourceType", UsageSourceType.REVIEW_JOB.name())
                            .count())
                    .isEqualTo(1.0);
            assertThat(missingUsage())
                    .as("malformed usage is not also counted as absent")
                    .isZero();
            verifyNoInteractions(usageAccumulator);
        }

        private double missingUsage() {
            return streamingMeterRegistry
                    .counter("llm.proxy.usage.missing", "sourceType", UsageSourceType.REVIEW_JOB.name())
                    .count();
        }

        @Test
        @DisplayName("the outgoing streaming request asks the provider to report usage")
        void theOutgoingRequestAsksForUsage() throws Exception {
            proxyStream("[DONE]");

            RecordedRequest sent = Objects.requireNonNull(upstream.takeRequest(5, TimeUnit.SECONDS));
            assertThat(OBJECT_MAPPER
                            .readTree(Objects.requireNonNull(sent.getBody()).utf8())
                            .path("stream_options")
                            .path("include_usage")
                            .asBoolean())
                    .isTrue();
        }

        @Test
        @DisplayName("a provider that rejects stream_options degrades to an unmetered call, not a failure")
        void aProviderThatRejectsTheUsageRequestDegrades() throws Exception {
            upstream.enqueue(new MockResponse.Builder()
                    .code(400)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"error\":{\"message\":\"Unrecognized request argument: stream_options\"}}")
                    .build());
            upstream.enqueue(new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: {\"choices\":[{\"delta\":{\"content\":\"served anyway\"}}]}\n\n")
                    .build());
            ProxyRouting routing = streamingRouting();
            authenticate(routing);
            stubCredential(
                    routing,
                    new LlmModelResolver.ProxyCredential(
                            upstream.url("/v1").toString(),
                            "openai-completions",
                            LlmAuthMode.BEARER,
                            "catalog-model",
                            "secret"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            controllerProxy(response);

            assertThat(response.getContentAsString()).contains("served anyway");
            RecordedRequest initial = Objects.requireNonNull(upstream.takeRequest(5, TimeUnit.SECONDS));
            RecordedRequest retry = Objects.requireNonNull(upstream.takeRequest(5, TimeUnit.SECONDS));
            assertThat(Objects.requireNonNull(initial.getBody()).utf8()).contains("stream_options");
            assertThat(Objects.requireNonNull(retry.getBody()).utf8()).doesNotContain("stream_options");
            // The refusal and the served response without usage are two facts, each counted once.
            assertThat(streamingMeterRegistry
                            .counter("llm.proxy.stream.usage.unsupported", "apiProtocol", "openai-completions")
                            .count())
                    .isEqualTo(1.0);
            assertThat(missingUsage()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("a streamed Responses call is billed from its completed event, unchanged on the way out")
        void aStreamedResponsesCallIsBilled() throws Exception {
            upstream.enqueue(new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body("event: response.output_text.delta\n"
                            + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hi\"}\n\n"
                            + "event: response.completed\n"
                            + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":100,"
                            + "\"input_tokens_details\":{\"cached_tokens\":25},\"output_tokens\":40,"
                            + "\"output_tokens_details\":{\"reasoning_tokens\":30}}}}\n\n")
                    .build());
            ProxyRouting routing = new ProxyRouting(
                    "job:stream",
                    "openai-responses",
                    upstream.url("/v1").toString(),
                    UsageFundingSource.INSTANCE,
                    7L,
                    8L,
                    9L,
                    ATTEMPT,
                    null,
                    null,
                    laterThanAnyTest());
            authenticate(routing);
            stubCredential(
                    routing,
                    new LlmModelResolver.ProxyCredential(
                            upstream.url("/v1").toString(),
                            "openai-responses",
                            LlmAuthMode.BEARER,
                            "catalog-model",
                            "secret"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            streamingController.proxy(
                    request("POST", "/internal/llm/responses"),
                    response,
                    new HttpHeaders(),
                    "{\"stream\":true,\"store\":false,\"include\":[\"reasoning.encrypted_content\"],\"input\":[]}"
                            .getBytes(StandardCharsets.UTF_8));

            assertThat(response.getContentAsString()).contains("\"delta\":\"hi\"");
            RecordedRequest sent = Objects.requireNonNull(upstream.takeRequest(5, TimeUnit.SECONDS));
            assertThat(sent.getUrl().encodedPath()).isEqualTo("/v1/responses");
            String sentBody = Objects.requireNonNull(sent.getBody()).utf8();
            assertThat(sentBody).contains("reasoning.encrypted_content").doesNotContain("stream_options");
            ArgumentCaptor<ProxyTokenUsage> usage = ArgumentCaptor.forClass(ProxyTokenUsage.class);
            verify(usageAccumulator).accumulate(eq(ATTEMPT), isNull(), usage.capture());
            assertThat(usage.getValue().billableInputTokens()).isEqualTo(75);
            assertThat(usage.getValue().outputTokens()).isEqualTo(40);
            assertThat(usage.getValue().reasoningTokens()).isEqualTo(30);
            assertThat(usage.getValue().cacheReadTokens()).isEqualTo(25);
        }

        // --- The attempt's work deadline -------------------------------------------------------------

        private static final String USAGE_FRAME =
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":40}}\n\n";
        private static final byte[] STREAM_REQUEST =
                "{\"stream\":true,\"messages\":[]}".getBytes(StandardCharsets.UTF_8);

        private void stubStreamCredential(ProxyRouting routing) {
            stubCredential(
                    routing,
                    new LlmModelResolver.ProxyCredential(
                            upstream.url("/v1").toString(),
                            "openai-completions",
                            LlmAuthMode.BEARER,
                            "catalog-model",
                            "secret"));
        }

        private @Nullable ResponseEntity<?> proxyReview(ProxyRouting routing, HttpServletResponse response) {
            authenticate(routing);
            return streamingController.proxy(
                    request("POST", "/internal/llm/chat/completions"), response, new HttpHeaders(), STREAM_REQUEST);
        }

        private void assertBilledOnceForTheUsageFrame() {
            ArgumentCaptor<ProxyTokenUsage> usage = ArgumentCaptor.forClass(ProxyTokenUsage.class);
            verify(usageAccumulator).accumulate(eq(ATTEMPT), isNull(), usage.capture());
            assertThat(usage.getValue().billableInputTokens()).isEqualTo(100);
            assertThat(usage.getValue().outputTokens()).isEqualTo(40);
            assertThat(missingUsage()).isZero();
        }

        private double errors() {
            return streamingMeterRegistry
                    .counter(AgentMetrics.LLM_PROXY_ERRORS, "apiProtocol", "openai-completions")
                    .count();
        }

        @Test
        @DisplayName(
                "a call after the attempt's work time makes no request and is refused with words no client retries")
        void aCallAfterTheWorkTimeMakesNoRequest() {
            ProxyRouting routing = streamingRouting(Instant.now().minusSeconds(1));
            MockHttpServletResponse response = new MockHttpServletResponse();

            var result = proxyReview(routing, response);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(403);
            assertThat(result.getBody()).isEqualTo(LlmProxyService.WORK_TIME_OVER);
            assertThat(LlmProxyService.WORK_TIME_OVER.toLowerCase(Locale.ROOT))
                    .doesNotContain("timeout", "timed out", "overload", "rate limit", "try again");
            assertThat(upstream.getRequestCount()).isZero();
            verify(resolver, never()).resolveProxyCredential(any());
            verifyNoInteractions(usageAccumulator);
            assertThat(response.isCommitted()).isFalse();
        }

        @Test
        @DisplayName("a deadline that passes before the headers writes nothing, bills nothing, and is refused")
        void aDeadlineBeforeTheHeadersWritesNothing() {
            upstream.enqueue(new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .headersDelay(4, TimeUnit.SECONDS)
                    .body(USAGE_FRAME)
                    .build());
            ProxyRouting routing = streamingRouting(Instant.now().plusSeconds(1));
            stubStreamCredential(routing);
            MockHttpServletResponse response = new MockHttpServletResponse();

            var result = proxyReview(routing, response);

            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(403);
            assertThat(result.getBody()).isEqualTo(LlmProxyService.WORK_TIME_OVER);
            // The stream that answers later cannot take the response over: no status, header or byte of it.
            assertThat(response.isCommitted()).isFalse();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getHeader("X-Accel-Buffering")).isNull();
            verifyNoInteractions(usageAccumulator);
            assertThat(missingUsage()).isZero();
            assertThat(upstream.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the retry without stream_options gets what is left of the deadline, not a new window")
        void theUsageRetryGetsWhatIsLeftOfTheDeadline() {
            upstream.enqueue(new MockResponse.Builder()
                    .code(400)
                    .addHeader("Content-Type", "application/json")
                    .headersDelay(1, TimeUnit.SECONDS)
                    .body("{\"error\":{\"message\":\"Unrecognized request argument: stream_options\"}}")
                    .build());
            upstream.enqueue(new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .headersDelay(3, TimeUnit.SECONDS)
                    .body(USAGE_FRAME)
                    .build());
            Instant deadline = Instant.now().plusSeconds(2);
            ProxyRouting routing = streamingRouting(deadline);
            stubStreamCredential(routing);
            MockHttpServletResponse response = new MockHttpServletResponse();

            var result = proxyReview(routing, response);

            // A fresh window would have waited out the 3 s headers and served the stream.
            assertThat(result).isNotNull();
            assertThat(result.getStatusCode().value()).isEqualTo(403);
            assertThat(Instant.now()).isBefore(deadline.plusSeconds(2));
            assertThat(upstream.getRequestCount()).isEqualTo(2);
            assertThat(response.isCommitted()).isFalse();
            verifyNoInteractions(usageAccumulator);
        }

        /** One SSE answer to every request, counting them; its body is the given one. */
        private WebClient answering(AtomicInteger requests, Flux<DataBuffer> body) {
            var answer = ClientResponse.create(HttpStatusCode.valueOf(200))
                    .header(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                    .body(body)
                    .build();
            return WebClient.builder()
                    .exchangeFunction(request -> {
                        requests.incrementAndGet();
                        return Mono.just(answer);
                    })
                    .build();
        }

        private static DataBuffer usageFrame() {
            return DefaultDataBufferFactory.sharedInstance.wrap(USAGE_FRAME.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * The write is held past the deadline and opened only by another thread once the upstream is cancelled, so
         * the cancel cannot have waited for the write; the request thread makes every response call.
         */
        @Test
        @DisplayName("the deadline cancels the upstream during a held client write; the request thread settles it once")
        void theDeadlineCancelsTheUpstreamDuringAHeldWrite() throws Exception {
            var cancelled = new CountDownLatch(1);
            var requests = new AtomicInteger();
            var service = controllerWith(answering(
                    requests, Flux.just(usageFrame()).concatWith(Flux.never()).doOnCancel(cancelled::countDown)));
            ProxyRouting routing = streamingRouting(Instant.now().plusSeconds(1));
            stubStreamCredential(routing);
            authenticate(routing);
            var callers = new ConcurrentLinkedQueue<Thread>();
            var writing = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            ServletOutputStream output = mock(ServletOutputStream.class, call -> {
                callers.add(Thread.currentThread());
                if (call.getMethod().getName().equals("write")) {
                    writing.countDown();
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                }
                return RETURNS_DEFAULTS.answer(call);
            });
            HttpServletResponse response = mock(HttpServletResponse.class, call -> {
                callers.add(Thread.currentThread());
                return call.getMethod().getName().equals("getOutputStream") ? output : RETURNS_DEFAULTS.answer(call);
            });
            var cancelledWhileHeld = new AtomicBoolean();
            var releaser = new Thread(() -> {
                try {
                    cancelledWhileHeld.set(
                            writing.await(10, TimeUnit.SECONDS) && cancelled.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    release.countDown();
                }
            });
            releaser.start();

            var result = service.proxy(
                    request("POST", "/internal/llm/chat/completions"), response, new HttpHeaders(), STREAM_REQUEST);
            releaser.join(10_000);

            assertThat(cancelledWhileHeld).isTrue();
            assertThat(result).isNull();
            assertThat(callers).isNotEmpty().containsOnly(Thread.currentThread());
            assertBilledOnceForTheUsageFrame();
            verify(response).setStatus(200);
            verify(response, never()).setStatus(403);
            verify(response, never()).setStatus(502);
            verify(response, never()).sendError(anyInt());
            assertThat(errors()).isEqualTo(1.0);
            assertThat(requests).hasValue(1);
        }

        @Test
        @DisplayName("a stream that fails after its usage frame keeps its committed response and is billed once")
        void aStreamThatFailsAfterItsUsageFrameIsBilledOnce() throws Exception {
            var requests = new AtomicInteger();
            var service = controllerWith(answering(
                    requests, Flux.just(usageFrame()).concatWith(Flux.error(new IOException("connection reset")))));
            ProxyRouting routing = streamingRouting();
            stubStreamCredential(routing);
            authenticate(routing);
            MockHttpServletResponse response = new MockHttpServletResponse();

            var result = service.proxy(
                    request("POST", "/internal/llm/chat/completions"), response, new HttpHeaders(), STREAM_REQUEST);

            assertThat(result).isNull();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo(USAGE_FRAME);
            assertBilledOnceForTheUsageFrame();
            assertThat(errors()).isEqualTo(1.0);
            assertThat(requests).hasValue(1);
        }

        @Test
        @DisplayName("the production client keeps its own timeouts and ends a stalled stream at the work deadline")
        void theConfiguredClientEndsAStalledStreamAtTheWorkDeadline() throws Exception {
            var config = new LlmProxyWebClientConfig();
            var pool = config.llmProxyConnectionProvider();
            var loops = config.llmProxyLoopResources();
            var streams = new AtomicInteger();
            var received = new AtomicReference<@Nullable Instant>();
            var cancelled = new CountDownLatch(1);
            // The usage frame, flushed on its own, then nothing: the stream ends only when the client cancels it.
            Flux<Publisher<? extends ByteBuf>> groups = Flux.concat(
                    Mono.just(Mono.fromSupplier(() -> Unpooled.copiedBuffer(USAGE_FRAME, StandardCharsets.UTF_8))),
                    Flux.never());
            var server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle((request, response) -> {
                        if (request.uri().startsWith("/warm"))
                            return Mono.from(response.status(204).send());
                        streams.incrementAndGet();
                        received.compareAndSet(null, Instant.now());
                        return request.receive()
                                .then()
                                .then(Mono.from(response.header(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                                        .sendGroups(groups.doOnCancel(cancelled::countDown))));
                    })
                    .bindNow(Duration.ofSeconds(10));
            try {
                String base = "http://127.0.0.1:" + server.port();
                WebClient configured = config.llmProxyWebClient(
                        pool,
                        loops,
                        new LlmProperties(
                                "", new LlmProperties.Egress(true), new LlmProperties.Fx(LlmProperties.ECB_DAILY_URL)),
                        WebClient.builder());
                // The client's first exchange also starts its event loop and opens its pooled connection. That
                // one-time start is no part of a work deadline, so it happens before the deadline is set.
                configured
                        .get()
                        .uri(base + "/warm")
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(10));
                var service = controllerWith(configured);
                Instant deadline = Instant.now().plusSeconds(1);
                ProxyRouting routing = streamingRouting(deadline);
                stubCredential(
                        routing,
                        new LlmModelResolver.ProxyCredential(
                                base + "/v1", "openai-completions", LlmAuthMode.BEARER, "catalog-model", "secret"));
                authenticate(routing);
                MockHttpServletResponse response = new MockHttpServletResponse();

                var result = service.proxy(
                        request("POST", "/internal/llm/chat/completions"), response, new HttpHeaders(), STREAM_REQUEST);

                assertThat(result)
                        .as("deadline %s; the upstream received the request at %s", deadline, received.get())
                        .isNull();
                assertThat(Instant.now()).isBefore(deadline.plusSeconds(5));
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getContentAsString()).isEqualTo(USAGE_FRAME);
                assertBilledOnceForTheUsageFrame();
                assertThat(errors()).isEqualTo(1.0);
                assertThat(streams).hasValue(1);
                // The upstream itself sees the stream cancelled.
                assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                server.disposeNow(Duration.ofSeconds(10));
                loops.dispose();
                pool.dispose();
            }
        }

        @ParameterizedTest(name = "mentor={0}")
        @ValueSource(booleans = {false, true})
        @DisplayName("the actual service lets a review stream cross 600 seconds while a mentor keeps its body limit")
        void shouldApplyTheBodyLimitOnlyToTheMentorStream(boolean mentor) throws Exception {
            var executor = Executors.newSingleThreadExecutor();
            // Created before virtual time: the blocking MVC request must have a real owning thread.
            var requestScheduler = Schedulers.fromExecutorService(executor);
            var firstWrite = new CountDownLatch(1);
            var tailArmed = new CountDownLatch(1);
            var requests = new AtomicInteger();
            var span = mock(Span.class);
            var spanBuilder = mock(Span.Builder.class, RETURNS_SELF);
            when(spanBuilder.start()).thenReturn(span);
            var tracer = mock(Tracer.class);
            when(tracer.spanBuilder()).thenReturn(spanBuilder);
            String lateFrame = "data: {\"choices\":[{\"delta\":{\"content\":\"after 600 seconds\"}}]}\n\n";
            var response = new MockHttpServletResponse();
            var delegate = response.getOutputStream();
            ServletOutputStream output = mock(ServletOutputStream.class, call -> {
                if (call.getMethod().getName().equals("write")) {
                    delegate.write(call.getArgument(0, byte[].class));
                    firstWrite.countDown();
                } else if (call.getMethod().getName().equals("flush")) {
                    delegate.flush();
                }
                return RETURNS_DEFAULTS.answer(call);
            });
            var servlet = new HttpServletResponseWrapper(response) {
                @Override
                public ServletOutputStream getOutputStream() {
                    return output;
                }
            };
            var billed = mentor
                    ? new ProxyRouting.BilledAttempt(
                            UsageSourceType.MENTOR_TURN, UUID.randomUUID(), 0, BigDecimal.ZERO, null)
                    : ATTEMPT;
            var routing = new ProxyRouting(
                    mentor ? "mentor-session:body-limit" : "job:body-limit",
                    "openai-completions",
                    upstream.url("/v1").toString(),
                    UsageFundingSource.INSTANCE,
                    7L,
                    8L,
                    9L,
                    billed,
                    null,
                    null,
                    mentor ? null : Instant.now().plus(Duration.ofHours(3)));
            stubStreamCredential(routing);
            try {
                StepVerifier.withVirtualTime(() -> Mono.fromCallable(() -> {
                                    var bodyConsumed = new AtomicBoolean();
                                    // An HTTP body is consumed once; response cleanup must not replay it.
                                    Flux<DataBuffer> body = Flux.defer(() -> {
                                        if (!bodyConsumed.compareAndSet(false, true)) return Flux.empty();
                                        return Flux.create(sink -> {
                                            var tail = Schedulers.parallel()
                                                    .schedule(
                                                            () -> {
                                                                sink.next(DefaultDataBufferFactory.sharedInstance.wrap(
                                                                        lateFrame.getBytes(StandardCharsets.UTF_8)));
                                                                sink.complete();
                                                            },
                                                            601,
                                                            TimeUnit.SECONDS);
                                            sink.onDispose(tail);
                                            sink.next(usageFrame());
                                            // The late frame's task must exist before virtual time can advance.
                                            tailArmed.countDown();
                                        });
                                    });
                                    var service = controllerWith(answering(requests, body), tracer);
                                    authenticate(routing);
                                    try {
                                        assertThat(service.proxy(
                                                        request("POST", "/internal/llm/chat/completions"),
                                                        servlet,
                                                        new HttpHeaders(),
                                                        STREAM_REQUEST))
                                                .isNull();
                                        return response.getContentAsString();
                                    } finally {
                                        SecurityContextHolder.clearContext();
                                    }
                                })
                                .subscribeOn(requestScheduler))
                        .then(() -> {
                            try {
                                assertThat(tailArmed.await(5, TimeUnit.SECONDS)).isTrue();
                                assertThat(firstWrite.await(5, TimeUnit.SECONDS))
                                        .isTrue();
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(interrupted);
                            }
                        })
                        .thenAwait(Duration.ofSeconds(601))
                        .expectNext(mentor ? USAGE_FRAME : USAGE_FRAME + lateFrame)
                        .expectComplete()
                        .verify(Duration.ofSeconds(10));
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.isCommitted()).isTrue();
                assertThat(requests).hasValue(1);
                assertThat(errors()).isEqualTo(mentor ? 1.0 : 0.0);
                verify(span).tag("hephaestus.stream.outcome", mentor ? "TIMED_OUT" : "COMPLETED");
                ArgumentCaptor<ProxyTokenUsage> usage = ArgumentCaptor.forClass(ProxyTokenUsage.class);
                if (mentor) {
                    verify(mentorTurnUsageAccumulator).accumulate(eq(billed), usage.capture());
                    verifyNoInteractions(usageAccumulator);
                } else {
                    verify(usageAccumulator).accumulate(eq(billed), isNull(), usage.capture());
                    verifyNoInteractions(mentorTurnUsageAccumulator);
                }
                assertThat(usage.getValue().billableInputTokens()).isEqualTo(100);
                assertThat(usage.getValue().outputTokens()).isEqualTo(40);
                assertThat(streamingMeterRegistry
                                .counter(
                                        "llm.proxy.usage.missing",
                                        "sourceType",
                                        billed.sourceType().name())
                                .count())
                        .isZero();
            } finally {
                requestScheduler.dispose();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("a review stream may wait all of its remaining work time; other job calls keep the proxy ceiling")
    void shouldGiveAReviewStreamItsRemainingWorkTimeWithoutTheOldStreamCap() {
        Duration ceiling = Duration.ofSeconds(910);
        Instant longJob = Instant.now().plus(Duration.ofHours(3));

        assertThat(LlmProxyService.exchangeWait(longJob, true)).isGreaterThan(Duration.ofHours(2));
        assertThat(LlmProxyService.exchangeWait(longJob, false)).isEqualTo(ceiling);
        assertThat(LlmProxyService.exchangeWait(Instant.now().plusSeconds(10), false))
                .isLessThanOrEqualTo(Duration.ofSeconds(10));
        assertThat(LlmProxyService.exchangeWait(Instant.now().minusSeconds(1), true))
                .isZero();
        // A mentor turn has no work deadline and keeps the proxy's own limits.
        assertThat(LlmProxyService.exchangeWait(null, true)).isEqualTo(ceiling);
    }

    @Nested
    class HeaderAllowlist {

        @Test
        void shouldInjectBearerAuthAndDropUnapprovedHeaders() {
            var incoming = incomingHeaders();

            HttpHeaders output =
                    controller.buildUpstreamHeaders(incoming, credential("openai-completions", LlmAuthMode.BEARER));

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
                    incomingHeaders(), credential("openai-responses", LlmAuthMode.API_KEY));

            assertThat(output.getFirst("api-key")).isEqualTo("secret");
            assertThat(output.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldNotInjectAuthWhenKeyIsBlank() {
            var credential = new LlmModelResolver.ProxyCredential(
                    "https://api.example.com/v1", "openai-completions", LlmAuthMode.BEARER, "catalog-model", " ");

            HttpHeaders output = controller.buildUpstreamHeaders(incomingHeaders(), credential);

            assertThat(output.get(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(output.get("api-key")).isNull();
        }
    }

    @Test
    void shouldBuildCanonicalProtocolUrls() {
        assertThat(LlmProxyService.buildUpstreamUri("https://api.example.com/v1/", LlmApiProtocol.OPENAI_COMPLETIONS))
                .isEqualTo(URI.create("https://api.example.com/v1/chat/completions"));
        assertThat(LlmProxyService.buildUpstreamUri("https://api.example.com/v1", LlmApiProtocol.OPENAI_RESPONSES))
                .isEqualTo(URI.create("https://api.example.com/v1/responses"));
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

    private double blocked(String apiProtocol, String cap) {
        return meterRegistry
                .counter("llm.proxy.budget.blocked", "apiProtocol", apiProtocol, "cap", cap)
                .count();
    }

    private static final ProxyRouting.BilledAttempt ATTEMPT = new ProxyRouting.BilledAttempt(
            UsageSourceType.REVIEW_JOB,
            UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            0,
            BigDecimal.ZERO,
            "worker-1");

    // A work deadline no test reaches, for job routes whose test is not about it.
    private static Instant laterThanAnyTest() {
        return Instant.now().plus(Duration.ofHours(3));
    }

    private static ProxyRouting routing(String protocol) {
        return new ProxyRouting(
                "job:test",
                protocol,
                "https://frozen.example.com/v1",
                UsageFundingSource.INSTANCE,
                7L,
                8L,
                9L,
                ATTEMPT,
                null,
                null,
                laterThanAnyTest());
    }

    private static ProxyRouting practiceScriptRouting(ModelKind slot, String protocol) {
        return practiceScriptRouting(slot, protocol, laterThanAnyTest());
    }

    private static ProxyRouting practiceScriptRouting(ModelKind slot, String protocol, Instant workDeadline) {
        return new ProxyRouting(
                "job:test:practice-script",
                protocol,
                "https://frozen.example.com/v1",
                UsageFundingSource.INSTANCE,
                7L,
                8L,
                9L,
                ATTEMPT,
                slot,
                DataHandlingTier.CLOUD,
                workDeadline);
    }

    private static ProxyRouting workspaceFundedRouting() {
        return new ProxyRouting(
                "job:test",
                "openai-completions",
                "https://frozen.example.com/v1",
                UsageFundingSource.WORKSPACE,
                7L,
                8L,
                9L,
                ATTEMPT,
                null,
                null,
                laterThanAnyTest());
    }

    private static ProxyRouting unboundMentorSessionRouting() {
        return new ProxyRouting(
                "mentor-session",
                "openai-completions",
                "https://frozen.example.com/v1",
                UsageFundingSource.INSTANCE,
                7L,
                8L,
                9L,
                null,
                null,
                null,
                null);
    }

    private static LlmModelResolver.ProxyCredential credential(String protocol, LlmAuthMode authMode) {
        return new LlmModelResolver.ProxyCredential(
                "https://api.example.com/v1", protocol, authMode, "catalog-model", "secret");
    }

    private void authenticate(ProxyRouting routing) {
        var identity = new ProxyIdentity(
                null, null, new MentorProxyCredentialRegistry.AuthenticatedSession(UUID.randomUUID(), "test-hash"));
        lenient().when(routingService.resolve(eq(identity), any())).thenReturn(routing);
        SecurityContextHolder.getContext().setAuthentication(new JobTokenAuthentication(identity, Set.of()));
    }

    private void stubCredential(ProxyRouting routing, LlmModelResolver.ProxyCredential credential) {
        when(resolver.resolveProxyCredential(eq(new LlmModelResolver.ConnectionRef(
                        routing.connectionScope(), routing.connectionId(), routing.modelId(), routing.workspaceId()))))
                .thenReturn(credential);
    }
}
