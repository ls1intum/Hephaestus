package de.tum.in.www1.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("JobTokenAuthenticationFilter")
class JobTokenAuthenticationFilterTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private FilterChain filterChain;

    private JobTokenAuthenticationFilter filter;

    /** A valid Base64-URL token (43 chars, 256 bits). */
    private static final String VALID_TOKEN = "dGVzdC10b2tlbi0xMjM0NTY3ODkwMTIzNDU2Nzg5MDE";
    private static final String VALID_TOKEN_HASH = AgentJob.computeTokenHash(VALID_TOKEN);

    @BeforeEach
    void setUp() {
        filter = new JobTokenAuthenticationFilter(agentJobRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("IP validation")
    class IpValidation {

        @Test
        @DisplayName("should reject non-private IP with 403")
        void shouldRejectNonPrivateIp() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("8.8.8.8");
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should accept loopback IP")
        void shouldAcceptLoopbackIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("127.0.0.1")).isTrue();
        }

        @Test
        @DisplayName("should accept 10.x.x.x")
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
        @DisplayName("should reject public IP")
        void shouldRejectPublicIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("1.2.3.4")).isFalse();
        }

        @Test
        @DisplayName("should reject null IP")
        void shouldRejectNullIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp(null)).isFalse();
        }

        @Test
        @DisplayName("should accept IPv6 loopback")
        void shouldAcceptIpv6Loopback() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("::1")).isTrue();
        }

        @Test
        @DisplayName("should accept IPv6 link-local")
        void shouldAcceptIpv6LinkLocal() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("fe80::1")).isTrue();
        }

        @Test
        @DisplayName("should reject malformed IP")
        void shouldRejectMalformedIp() {
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("not-an-ip")).isFalse();
        }

        @Test
        @DisplayName("should reject hostname to prevent DNS resolution")
        void shouldRejectHostname() {
            // Hostnames must be rejected before InetAddress.getByName() to prevent DNS resolution
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("localhost")).isFalse();
            assertThat(JobTokenAuthenticationFilter.isPrivateIp("internal.corp")).isFalse();
        }
    }

    @Nested
    @DisplayName("Token extraction and validation")
    class TokenValidation {

        @Test
        @DisplayName("should return 401 when no auth header present")
        void shouldReturn401WhenNoAuthHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should extract token from x-api-key header (Anthropic)")
        void shouldExtractFromXApiKey() throws Exception {
            var job = createRunningJob();
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));

            // Capture authentication during filter chain execution (before finally clears it)
            var authCapture = new AtomicReference<Authentication>();
            doAnswer(invocation -> {
                authCapture.set(SecurityContextHolder.getContext().getAuthentication());
                return null;
            })
                .when(filterChain)
                .doFilter(any(), any());

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(filterChain).doFilter(any(), any());
            assertThat(authCapture.get()).isInstanceOf(JobTokenAuthentication.class);
            // Verify context is cleaned up after filter chain completes
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should extract token from Authorization Bearer header (OpenAI)")
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
            assertThat(((JobTokenAuthentication) authCapture.get()).getPrincipal()).isSameAs(job);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should extract token from api-key header (Azure)")
        void shouldExtractFromAzureApiKey() throws Exception {
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
            request.addHeader("api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(filterChain).doFilter(any(), any());
            assertThat(authCapture.get()).isInstanceOf(JobTokenAuthentication.class);
            assertThat(((JobTokenAuthentication) authCapture.get()).getPrincipal()).isSameAs(job);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should return 401 for malformed token (not Base64-URL)")
        void shouldReturn401ForMalformedToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", "not a valid base64!!!");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should return 401 when no RUNNING job matches token hash")
        void shouldReturn401WhenNoRunningJobFound() throws Exception {
            when(agentJobRepository.findByJobTokenHashAndStatus(any(), eq(AgentJobStatus.RUNNING))).thenReturn(
                Optional.empty()
            );

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should return 401 for whitespace-only token")
        void shouldReturn401ForWhitespaceOnlyToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", "   ");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should return 401 for empty Bearer (Authorization: Bearer )")
        void shouldReturn401ForEmptyBearer() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "Bearer ");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should accept case-insensitive Bearer prefix")
        void shouldAcceptCaseInsensitiveBearer() throws Exception {
            var job = createRunningJob();
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Authorization", "bearer " + VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should prefer x-api-key over Authorization header when both present")
        void shouldPreferXApiKeyOverBearer() throws Exception {
            var job = createRunningJob();
            when(
                agentJobRepository.findByJobTokenHashAndStatus(eq(VALID_TOKEN_HASH), eq(AgentJobStatus.RUNNING))
            ).thenReturn(Optional.of(job));

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", VALID_TOKEN);
            request.addHeader("Authorization", "Bearer some-other-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // Should authenticate with x-api-key token (VALID_TOKEN), not the Bearer token
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should clear SecurityContext even when filter chain throws")
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
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (jakarta.servlet.ServletException expected) {
                // Expected
            }

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("should reject token with Base64 padding characters")
        void shouldRejectTokenWithPadding() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("x-api-key", "dGVzdA==");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should not be bypassed by X-Forwarded-For header spoofing")
        void shouldNotBeBypassedByXForwardedFor() throws Exception {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("8.8.8.8"); // Public IP
            request.addHeader("X-Forwarded-For", "10.0.0.2"); // Spoofed private IP
            request.addHeader("x-api-key", VALID_TOKEN);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // Should still reject — uses getRemoteAddr(), not X-Forwarded-For
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    private AgentJob createRunningJob() {
        var job = mock(AgentJob.class);
        when(job.getJobToken()).thenReturn(VALID_TOKEN);
        return job;
    }
}
