package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class JobTokenAuthenticationTest extends BaseUnitTest {

    @Test
    void shouldReturnJobAsPrincipal() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getPrincipal()).isSameAs(job);
    }

    @Test
    void shouldRedactCredentials() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getCredentials()).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldBeAuthenticated() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldHaveEmptyAuthorities() {
        AgentJob job = mock(AgentJob.class);
        var auth = new JobTokenAuthentication(job);

        assertThat(auth.getAuthorities()).isEmpty();
    }
}
