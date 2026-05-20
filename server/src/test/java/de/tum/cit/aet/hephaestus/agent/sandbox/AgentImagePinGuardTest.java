package de.tum.cit.aet.hephaestus.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentImagePinGuardTest extends BaseUnitTest {

    @Test
    void shouldAllowStartupWhenReferenceIsDigestPinned() {
        var props = new AgentImageProperties("ghcr.io/x/agent-pi@sha256:" + "a".repeat(64), ImagePullPolicy.ALWAYS);
        assertThatCode(() -> new AgentImagePinGuard(props)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ghcr.io/x/agent-pi:latest", "ghcr.io/x/agent-pi@sha256:abc123" })
    void shouldFailFastWhenReferenceIsNotDigestPinned(String reference) {
        var props = new AgentImageProperties(reference, ImagePullPolicy.ALWAYS);
        assertThatThrownBy(() -> new AgentImagePinGuard(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(reference)
            .hasMessageContaining("hephaestus.agent.image.require-digest")
            .hasMessageContaining("docs/admin/agent-image-digests.md");
    }
}
