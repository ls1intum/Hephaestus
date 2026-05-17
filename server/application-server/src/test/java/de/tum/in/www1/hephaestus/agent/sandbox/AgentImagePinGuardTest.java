package de.tum.in.www1.hephaestus.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class AgentImagePinGuardTest extends BaseUnitTest {

    private static final String TAG_REF = "ghcr.io/x/agent-pi:latest";

    @Test
    void shouldFailFastWhenReferenceIsTagOnly() {
        var props = new AgentImageProperties(TAG_REF, ImagePullPolicy.ALWAYS, true);
        assertThatThrownBy(() -> new AgentImagePinGuard(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(TAG_REF)
            .hasMessageContaining(AgentImageProperties.DIGEST_SUFFIX_DESCRIPTION)
            .hasMessageContaining("docs/admin/agent-image-digests.md");
    }
}
