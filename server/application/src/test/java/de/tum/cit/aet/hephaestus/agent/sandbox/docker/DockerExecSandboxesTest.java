package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class DockerExecSandboxesTest extends BaseUnitTest {

    @Test
    void shouldTellTwoNativeWorkersOnOneMachineApartAndKeepEachAcrossRestarts() {
        assertThat(DockerExecSandboxes.machineIdentity("build-host", 8080))
                .isEqualTo("machine:build-host:8080")
                .isEqualTo(DockerExecSandboxes.machineIdentity("build-host", 8080))
                .isNotEqualTo(DockerExecSandboxes.machineIdentity("build-host", 8081));
    }
}
