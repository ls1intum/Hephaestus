package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class NetworkPolicyTest extends BaseUnitTest {

    @Test
    void nullAndBlankUrlsArePermitted() {
        assertThat(new NetworkPolicy(false, null, null, null)).isNotNull();
        assertThat(new NetworkPolicy(false, "", null, null)).isNotNull();
        assertThat(new NetworkPolicy(false, "   ", null, null)).isNotNull();
    }

    @Test
    void httpAndHttpsAreAccepted() {
        assertThat(new NetworkPolicy(true, "http://localhost:8080", "tok", "anthropic")).isNotNull();
        assertThat(new NetworkPolicy(true, "https://proxy.example/internal", "tok", "openai")).isNotNull();
    }

    @Test
    void relativeUrlRejected() {
        assertThatThrownBy(() -> new NetworkPolicy(true, "/internal/llm", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be absolute");
    }

    @Test
    void nonHttpSchemeRejected() {
        assertThatThrownBy(() -> new NetworkPolicy(true, "ftp://x.example", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http or https");
        assertThatThrownBy(() -> new NetworkPolicy(true, "file:///etc/passwd", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http or https");
    }

    @Test
    void schemeCheckIsCaseInsensitive() {
        assertThat(new NetworkPolicy(false, "HTTPS://proxy.example", "tok", "p")).isNotNull();
    }
}
