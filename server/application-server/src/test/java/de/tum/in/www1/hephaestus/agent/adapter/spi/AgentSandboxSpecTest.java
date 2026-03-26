package de.tum.in.www1.hephaestus.agent.adapter.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AgentSandboxSpec")
class AgentSandboxSpecTest extends BaseUnitTest {

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject null image")
        void shouldRejectNullImage() {
            assertThatThrownBy(() ->
                new AgentSandboxSpec(null, null, null, null, "/output", null, null, null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank image")
        void shouldRejectBlankImage() {
            assertThatThrownBy(() ->
                new AgentSandboxSpec("  ", null, null, null, "/output", null, null, null)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject null outputPath")
        void shouldRejectNullOutputPath() {
            assertThatThrownBy(() ->
                new AgentSandboxSpec("alpine:latest", null, null, null, null, null, null, null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject blank outputPath")
        void shouldRejectBlankOutputPath() {
            assertThatThrownBy(() ->
                new AgentSandboxSpec("alpine:latest", null, null, null, "  ", null, null, null)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("should default null command to empty list")
        void shouldDefaultNullCommandToEmptyList() {
            var spec = new AgentSandboxSpec("alpine:latest", null, null, null, "/output", null, null, null);
            assertThat(spec.command()).isEmpty();
        }

        @Test
        @DisplayName("should default null environment to empty map")
        void shouldDefaultNullEnvironmentToEmptyMap() {
            var spec = new AgentSandboxSpec("alpine:latest", List.of("echo"), null, null, "/output", null, null, null);
            assertThat(spec.environment()).isEmpty();
        }

        @Test
        @DisplayName("should default null inputFiles to empty map")
        void shouldDefaultNullInputFilesToEmptyMap() {
            var spec = new AgentSandboxSpec(
                "alpine:latest",
                List.of("echo"),
                Map.of("FOO", "bar"),
                null,
                "/output",
                null,
                null,
                null
            );
            assertThat(spec.inputFiles()).isEmpty();
        }

        @Test
        @DisplayName("should accept null securityProfile and networkPolicy")
        void shouldAcceptNullSecurityProfileAndNetworkPolicy() {
            var spec = new AgentSandboxSpec("alpine:latest", null, null, null, "/output", null, null, null);
            assertThat(spec.securityProfile()).isNull();
            assertThat(spec.networkPolicy()).isNull();
        }
    }
}
