package de.tum.cit.aet.hephaestus.agent;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.settings.AgentConfigurationUnavailableException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentControllerAdviceTest extends BaseUnitTest {

    @Test
    void unavailableBindingUsesSurfaceNeutralProblemDetail() {
        var problem = new AgentControllerAdvice().handleAgentConfigurationUnavailable(
            new AgentConfigurationUnavailableException()
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Agent configuration unavailable");
        assertThat(problem.getDetail()).contains("agent configuration");
    }
}
