package de.tum.in.www1.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AgentImageProperties")
class AgentImagePropertiesTest extends BaseUnitTest {

    @ParameterizedTest(name = "isDigestPinned({0}) = {1}")
    @CsvSource(
        textBlock = """
        ghcr.io/x/agent-pi@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, true
        ghcr.io/x/agent-pi:latest,                                                                false
        ghcr.io/x/agent-pi@sha256:abc123,                                                         false
        ghcr.io/x/agent-pi@sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA, false
        """
    )
    void isDigestPinned(String reference, boolean expected) {
        var props = new AgentImageProperties(reference, ImagePullPolicy.IF_NOT_PRESENT, false);
        assertThat(props.isDigestPinned()).isEqualTo(expected);
    }
}
