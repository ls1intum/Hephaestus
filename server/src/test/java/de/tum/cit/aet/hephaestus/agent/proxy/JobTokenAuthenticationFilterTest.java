package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class JobTokenAuthenticationFilterTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private FilterChain filterChain;

    private MentorProxyCredentialRegistry mentorRegistry;
    private JobTokenAuthenticationFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** A valid Base64-URL token (43 chars, 256 bits). */
    private static final String VALID_TOKEN = "dGVzdC10b2tlbi0xMjM0NTY3ODkwMTIzNDU2Nzg5MDE";
    private static final String VALID_TOKEN_HASH = AgentJob.computeTokenHash(VALID_TOKEN);

    @BeforeEach
    void setUp() {
        mentorRegistry = new MentorProxyCredentialRegistry();
        filter = new JobTokenAuthenticationFilter(agentJobRepository, mentorRegistry, objectMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class IpValidation {

        @Test
        void shouldRejectNonPrivateIp() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("8.8.8.8");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        void shouldAcceptLoopbackIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("127.0.0.1")).isTrue();
        }

        @Test
        void shouldAcceptClassA() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("10.0.0.5")).isTrue();
        }

        @Test
        @DisplayName("should accept 172.16.x.x")
        void shouldAcceptClassB() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("172.18.0.2")).isTrue();
        }

        @Test
        @DisplayName("should accept 192.168.x.x")
        void shouldAcceptClassC() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("192.168.1.1")).isTrue();
        }

        @Test
        void shouldRejectPublicIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("1.2.3.4")).isFalse();
        }

        @Test
        void shouldRejectNullIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp(null)).isFalse();
        }

        @Test
        void shouldAcceptIpv6Loopback() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("::1")).isTrue();
        }

        @Test
        void shouldAcceptIpv6LinkLocal() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("fe80::1")).isTrue();
        }

        @Test
        void shouldRejectMalformedIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("not-an-ip")).isFalse();
        }

        @Test
        void shouldRejectHostname() {
            // Hostnames must be rejected before InetAddress.getByName() to prevent DNS resolution
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("localhost")).isFalse();
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("internal.corp")).isFalse();
        }
    }

    @Nested
    class TokenValidation {

        @Test
        void shouldReturn401WhenNoAuthHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        void shouldRejectXApiKeyProviderHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        void shouldExtractFromBearerAuth() throws Exception {
            var job = createRunningJob();
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));

            var authCapture = new AtomicReference<Authentication>();
            doAnswer(invocation -> {
                authCapture.set(SecurityContextHolder.getContext().getAuthentication());
                return null;
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(filterChain).doFilter(any(), any());
            assertThat(authCapture.get()).isInstanceOf(JobTokenAuthentication.class);
            ProxyRouting routing = (ProxyRouting) ((JobTokenAuthentication) authCapture.get()).getPrincipal();
            assertThat(routing.principalDescription()).isEqualTo("job:" + job.getId());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldRejectApiKeyProviderHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        void shouldReturn401ForMalformedToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer not a valid base64!!!");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        void shouldReturn401WhenNoRunningJobFound() throws Exception {
            when(agentJobRepository.findByJobTokenHashAndStatus(any(), eq(AgentJobStatus.RUNNING))).thenReturn(
                Optional.empty()
            );

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName(
            "a job with no config snapshot (should be impossible in prod — column is NOT NULL) is refused, not NPE'd"
        )
        void shouldReturn401WhenJobHasNoConfigSnapshot() throws Exception {
            var job = new AgentJob();
            job.setJobToken(VALID_TOKEN);
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @ParameterizedTest(name = "Authorization: [{0}]")
        @ValueSource(strings = { "Bearer ", "Bearer    " })
        void shouldReturn401ForABearerHeaderWithNoToken(String header) throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", header);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        void shouldAcceptCaseInsensitiveBearer() throws Exception {
            var job = createRunningJob();

            Authentication installed = authenticateAndCaptureAuthentication(job, "bearer " + VALID_TOKEN, null);

            assertThatTheChainRanAsTheJob(installed, job);
        }

        @Test
        void shouldIgnoreProviderKeyWhenBearerIsPresent() throws Exception {
            var job = createRunningJob();

            Authentication installed = authenticateAndCaptureAuthentication(
                job,
                "Bearer " + VALID_TOKEN,
                "provider-key-must-be-ignored"
            );

            assertThatTheChainRanAsTheJob(installed, job);
        }

        /**
         * MockHttpServletResponse's status defaults to 200, so asserting SC_OK proves nothing on its
         * own — a filter that forwarded the chain WITHOUT installing the job's authentication would
         * pass that. What has to hold is that the downstream chain ran as this job.
         */
        private void assertThatTheChainRanAsTheJob(Authentication installed, AgentJob job) {
            assertThat(installed).isInstanceOf(JobTokenAuthentication.class);
            ProxyRouting routing = (ProxyRouting) ((JobTokenAuthentication) installed).getPrincipal();
            assertThat(routing.principalDescription()).isEqualTo("job:" + job.getId());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        private Authentication authenticateAndCaptureAuthentication(
            AgentJob job,
            String authorizationHeader,
            @Nullable String providerKeyHeader
        ) throws Exception {
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));
            var authCapture = new AtomicReference<Authentication>();
            doAnswer(invocation -> {
                authCapture.set(SecurityContextHolder.getContext().getAuthentication());
                return null;
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            if (providerKeyHeader != null) {
                request.addHeader("x-api-key", providerKeyHeader);
            }
            request.addHeader("Authorization", authorizationHeader);

            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
            return authCapture.get();
        }

        @Test
        void shouldClearContextOnFilterChainException() throws Exception {
            var job = createRunningJob();
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));
            doAnswer(invocation -> {
                throw new jakarta.servlet.ServletException("Simulated failure");
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (jakarta.servlet.ServletException expected) {
                // no-op
            }

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldRejectTokenWithPadding() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer dGVzdA==");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        void shouldNotBeBypassedByXForwardedFor() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("8.8.8.8");
            request.addHeader("X-Forwarded-For", "10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // Uses getRemoteAddr(), not X-Forwarded-For
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Mentor session token fallback — the mentor sandbox is not an AgentJob row")
    class MentorTokenFallback {

        @Test
        void aMentorRegistryTokenAuthenticatesWhenNoJobMatches() throws Exception {
            when(agentJobRepository.findByJobTokenHashAndStatus(any(), eq(AgentJobStatus.RUNNING))).thenReturn(
                Optional.empty()
            );
            String mentorToken = mentorRegistry.mint(
                UUID.randomUUID(),
                new MentorProxyCredentialRegistry.Route(
                    "openai-completions",
                    "https://api.openai.com",
                    null,
                    null,
                    null,
                    null
                )
            );

            var authCapture = new AtomicReference<Authentication>();
            doAnswer(invocation -> {
                authCapture.set(SecurityContextHolder.getContext().getAuthentication());
                return null;
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + mentorToken);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            ProxyRouting routing = (ProxyRouting) ((JobTokenAuthentication) authCapture.get()).getPrincipal();
            assertThat(routing.apiProtocol()).isEqualTo("openai-completions");
        }

        @Test
        void aTokenMatchingNeitherJobNorMentorRegistryIsRefused() throws Exception {
            when(agentJobRepository.findByJobTokenHashAndStatus(any(), eq(AgentJobStatus.RUNNING))).thenReturn(
                Optional.empty()
            );

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("the billed attempt resolved from the row")
    class BilledAttempt {

        @Test
        @DisplayName("carries the row's retry_count, so a later attempt's write can be told apart")
        void theRoutingCarriesTheAttemptNumber() throws Exception {
            var job = createRunningJob();
            job.setRetryCount(3);

            ProxyRouting routing = authenticateAndCaptureRouting(job);

            assertThat(routing.attempt()).isNotNull();
            assertThat(routing.attempt().number()).isEqualTo(3);
            assertThat(routing.attempt().sourceId()).isEqualTo(job.getId());
        }

        /** 1M input at $10/1M plus 100k output at $30/1M is the $13 asserted below. */
        @Test
        @DisplayName("prices the calls the attempt has already made with the rates frozen at admission")
        void theRoutingPricesWhatTheAttemptHasAlreadySpent() throws Exception {
            var job = createRunningJob(
                new LlmPriceSnapshot(
                    FundingSource.INSTANCE,
                    PricingState.PRICED,
                    1L,
                    null,
                    new BigDecimal("10"),
                    new BigDecimal("30"),
                    new BigDecimal("1"),
                    new BigDecimal("2")
                )
            );
            job.setLlmTotalInputTokens(1_000_000);
            job.setLlmTotalOutputTokens(100_000);

            ProxyRouting routing = authenticateAndCaptureRouting(job);

            assertThat(routing.inFlightSpendUsd()).isEqualByComparingTo("13");
        }

        /** Only reachable for a job that never went through {@code LlmAdmissionService}, which refuses one. */
        @Test
        @DisplayName("a snapshot with no frozen price contributes no in-flight spend")
        void aSnapshotWithoutAPriceContributesNothing() throws Exception {
            var job = createRunningJob();
            job.setLlmTotalInputTokens(1_000_000);

            ProxyRouting routing = authenticateAndCaptureRouting(job);

            assertThat(routing.inFlightSpendUsd()).isEqualByComparingTo("0");
        }

        private ProxyRouting authenticateAndCaptureRouting(AgentJob job) throws Exception {
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));
            var authCapture = new AtomicReference<Authentication>();
            doAnswer(invocation -> {
                authCapture.set(SecurityContextHolder.getContext().getAuthentication());
                return null;
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

            return (ProxyRouting) ((JobTokenAuthentication) authCapture.get()).getPrincipal();
        }
    }

    private AgentJob createRunningJob() {
        return createRunningJob(null);
    }

    private AgentJob createRunningJob(LlmPriceSnapshot price) {
        var job = new AgentJob();
        job.setJobToken(VALID_TOKEN);
        ConfigSnapshot snapshot = new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            "openai-completions",
            "https://api.openai.com/v1",
            "gpt-5-mini",
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            600,
            false,
            price
        );
        job.setConfigSnapshot(snapshot.toJson(objectMapper));
        job.setId(java.util.UUID.randomUUID());
        Workspace workspace = new Workspace();
        workspace.setId(7L);
        job.setWorkspace(workspace);
        return job;
    }
}
