package de.tum.in.www1.hephaestus.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentImagePinGuard")
class AgentImagePinGuardTest extends BaseUnitTest {

    private static final String DIGEST_REF = "ghcr.io/x/agent-pi@sha256:" + "a".repeat(64);
    private static final String TAG_REF = "ghcr.io/x/agent-pi:latest";

    @Test
    @DisplayName("digest-pinned reference: constructor returns normally")
    void shouldAllowStartupWhenReferenceIsDigestPinned() {
        var props = new AgentImageProperties(DIGEST_REF, ImagePullPolicy.ALWAYS, true);
        assertThatCode(() -> new AgentImagePinGuard(props)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tag reference: constructor throws with bad value, expected shape, and doc pointer")
    void shouldFailFastWhenReferenceIsTagOnly() {
        var props = new AgentImageProperties(TAG_REF, ImagePullPolicy.ALWAYS, true);
        assertThatThrownBy(() -> new AgentImagePinGuard(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(TAG_REF)
            .hasMessageContaining(AgentImageProperties.DIGEST_SUFFIX_DESCRIPTION)
            .hasMessageContaining("docs/admin/agent-image-digests.md");
    }
}
