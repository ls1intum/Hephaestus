package de.tum.cit.aet.hephaestus.agent;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobStateConflictException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentControllerAdviceTest extends BaseUnitTest {

    @Test
    void jobStateConflictUsesSurfaceNeutralProblemDetail() {
        var problem = new AgentControllerAdvice().handleAgentJobStateConflict(
            new AgentJobStateConflictException("Job is already COMPLETED")
        );

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Agent job state conflict");
        assertThat(problem.getDetail()).isEqualTo("Job is already COMPLETED");
    }

    @Test
    void aBlankMessageFallsBackToAGenericDetail() {
        // ProblemDetail.detail must never be blank: the UI renders it verbatim.
        var problem = new AgentControllerAdvice().handleAgentJobStateConflict(new AgentJobStateConflictException(" "));

        assertThat(problem.getDetail()).isEqualTo("The agent request could not be processed.");
    }
}
