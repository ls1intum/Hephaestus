package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class DockerSandboxConfigurationTest extends BaseUnitTest {

    @Test
    void shouldOutliveTheLongestSandboxWhenWaitingForAContainerToExit() {
        // `docker wait` is silent until the container exits, so this timeout is a ceiling on the
        // container's life. At or below MAX_RUNTIME it would cut a legitimate wait short.
        assertThat(DockerSandboxConfiguration.HTTP_STREAMING_RESPONSE_TIMEOUT)
                .isGreaterThan(ResourceLimits.MAX_RUNTIME);
    }

    @Test
    void shouldGiveOrdinaryRequestsMoreThanTheLongestBudgetedCallCanTake() {
        // pullImage budgets itself 5 minutes; a shorter idle timeout would pre-empt that budget.
        assertThat(DockerSandboxConfiguration.HTTP_RESPONSE_TIMEOUT)
                .isGreaterThan(DockerClientOperations.IMAGE_PULL_TIMEOUT);
    }
}
