package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.RuntimeJobTestData;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JobJwt;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtVerifier;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JobTokenAuthenticationFilterTest extends BaseUnitTest {
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final WorkerJwtVerifier verifier = mock(WorkerJwtVerifier.class);
    private final JobTokenAuthenticationFilter filter =
            new JobTokenAuthenticationFilter(jobs, verifier, new MentorProxyCredentialRegistry(), "worker-1");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void admissionAuthenticatesWithoutConfigurationOrSpendQueries() throws Exception {
        var job = running();
        var jwt = jwt(job.getId(), job.getWorkspaceId(), job.getAttempt());
        when(verifier.verify("valid-token")).thenReturn(jwt);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        var request = request("/internal/llm/prepare-feedback", "Bearer valid-token");
        var response = new MockHttpServletResponse();
        var principal = new AtomicReference<Authentication>();
        filter.doFilter(
                request,
                response,
                (incoming, outgoing) -> principal.set(Objects.requireNonNull(
                        SecurityContextHolder.getContext().getAuthentication())));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(principal.get()).isInstanceOf(JobTokenAuthentication.class);
        assertThat(((JobTokenAuthentication) principal.get()).getPrincipal().job())
                .isEqualTo(jwt);
        assertThat(job.getConfigSnapshot()).isNull();
        verify(jobs).findById(job.getId());
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void mentorAuthenticationDoesNotRequireTurnPricing() throws Exception {
        var registry = new MentorProxyCredentialRegistry();
        var session = UUID.randomUUID();
        var token = registry.mint(
                session,
                new MentorProxyCredentialRegistry.Route(
                        "openai-responses", "https://models.example/v1", null, null, null, null));
        var meter = spy(new MentorTurnMeter(UUID.randomUUID(), LlmPriceSnapshot.unpricedInstance()));
        doThrow(new IllegalStateException("Pricing unavailable")).when(meter).spentUsd();
        assertThat(registry.bindTurn(session, meter)).isTrue();
        var authFilter = new JobTokenAuthenticationFilter(jobs, verifier, registry, "worker-1");
        var response = new MockHttpServletResponse();
        var principal = new AtomicReference<Authentication>();
        authFilter.doFilter(
                request("/internal/llm/responses", "Bearer " + token),
                response,
                (incoming, outgoing) -> principal.set(Objects.requireNonNull(
                        SecurityContextHolder.getContext().getAuthentication())));
        assertThat(response.getStatus()).isEqualTo(200);
        var sessionIdentity = Objects.requireNonNull(
                ((JobTokenAuthentication) principal.get()).getPrincipal().mentor());
        assertThat(sessionIdentity.sessionId()).isEqualTo(session);
        verifyNoInteractions(jobs, verifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"attempt", "workspace", "worker", "terminal", "path"})
    void rejectsCredentialOutsideItsExactRunningAttempt(String mismatch) throws Exception {
        var job = running();
        var jwt = jwt(
                job.getId(),
                mismatch.equals("workspace") ? 999L : job.getWorkspaceId(),
                mismatch.equals("attempt") ? job.getAttempt() + 1 : job.getAttempt());
        if (mismatch.equals("worker")) job.setWorkerId("other-worker");
        if (mismatch.equals("terminal")) job.setStatus(ReviewJobStatus.COMPLETED);
        when(verifier.verify("valid-token")).thenReturn(jwt);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        var request = request(
                mismatch.equals("path")
                        ? "/internal/llm/runtime/" + UUID.randomUUID()
                        : "/internal/llm/prepare-feedback",
                "Bearer valid-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
            throw new AssertionError("Unauthenticated request served");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonOwnerResultUploadReportsConflictOnlyForItsOwnRuntimeUrl() throws Exception {
        var job = running();
        job.setWorkerId("other-worker");
        when(verifier.verify("valid-token")).thenReturn(jwt(job.getId(), job.getWorkspaceId(), job.getAttempt()));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        for (var id : new UUID[] {job.getId(), UUID.randomUUID()}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(
                    request("/internal/llm/runtime/" + id + "/result", "Bearer valid-token"),
                    response,
                    (incoming, outgoing) -> {
                        throw new AssertionError("Non-owner upload served");
                    });
            assertThat(response.getStatus()).isEqualTo(id.equals(job.getId()) ? 409 : 401);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Bearer one two", "Basic one", "bearer "})
    void rejectsMalformedAuthorizationWithoutLookingUpAJob(String header) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/internal/llm/prepare-feedback", header), response, (incoming, outgoing) -> {
            throw new AssertionError("Malformed credential served");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(verifier, jobs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "deadbeef", "localhost", "2001:db8::1"})
    void rejectsNonprivateLiteralsAndHostnamesBeforeCredentialVerification(String address) throws Exception {
        var request = request("/internal/llm/prepare-feedback", "Bearer valid-token");
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
            throw new AssertionError("Public gateway served");
        });
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(verifier, jobs);
    }

    private static ReviewJob running() {
        var job = new ReviewJob();
        job.setId(UUID.randomUUID());
        RuntimeJobTestData.initialize(job, 7L);
        job.setStatus(ReviewJobStatus.RUNNING);
        job.setWorkerId("worker-1");
        job.setAttempt(3);
        return job;
    }

    private static JobJwt jwt(UUID id, long workspace, int attempt) {
        var now = Instant.now();
        return new JobJwt(
                id, workspace, attempt, Set.of("llm_proxy"), UUID.randomUUID().toString(), now, now.plusSeconds(60));
    }

    private static MockHttpServletRequest request(String path, String authorization) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Authorization", authorization);
        return request;
    }
}
