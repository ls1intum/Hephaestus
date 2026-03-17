package de.tum.in.www1.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JobTokenAuthentication")
class JobTokenAuthenticationTest extends BaseUnitTest {

    @Test
    @DisplayName("should return job as principal")
    void shouldReturnJobAsPrincipal() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getPrincipal()).isSameAs(job);
    }

    @Test
    @DisplayName("should redact credentials to prevent token leakage")
    void shouldRedactCredentials() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getCredentials()).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("should be authenticated on construction")
    void shouldBeAuthenticated() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("should have empty authorities")
    void shouldHaveEmptyAuthorities() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getAuthorities()).isEmpty();
    }
}
