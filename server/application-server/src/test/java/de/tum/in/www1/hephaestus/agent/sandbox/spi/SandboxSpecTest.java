package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SandboxSpec")
class SandboxSpecTest extends BaseUnitTest {

    @Test
    @DisplayName("should reject null jobId")
    void shouldRejectNullJobId() {
        assertThatNullPointerException()
            .isThrownBy(() ->
                new SandboxSpec(
                    null,
                    "alpine:latest",
                    List.of(),
                    Map.of(),
                    null,
                    ResourceLimits.DEFAULT,
                    SecurityProfile.DEFAULT,
                    Map.of(),
                    "/workspace/.output"
                )
            )
            .withMessageContaining("jobId");
    }

    @Test
    @DisplayName("should reject null image")
    void shouldRejectNullImage() {
        assertThatNullPointerException()
            .isThrownBy(() ->
                new SandboxSpec(
                    UUID.randomUUID(),
                    null,
                    List.of(),
                    Map.of(),
                    null,
                    ResourceLimits.DEFAULT,
                    SecurityProfile.DEFAULT,
                    Map.of(),
                    "/workspace/.output"
                )
            )
            .withMessageContaining("image");
    }

    @Test
    @DisplayName("should reject blank image")
    void shouldRejectBlankImage() {
        assertThatThrownBy(() ->
            new SandboxSpec(
                UUID.randomUUID(),
                "  ",
                List.of(),
                Map.of(),
                null,
                ResourceLimits.DEFAULT,
                SecurityProfile.DEFAULT,
                Map.of(),
                "/workspace/.output"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("should reject null resourceLimits")
    void shouldRejectNullResourceLimits() {
        assertThatNullPointerException()
            .isThrownBy(() ->
                new SandboxSpec(
                    UUID.randomUUID(),
                    "alpine:latest",
                    List.of(),
                    Map.of(),
                    null,
                    null,
                    SecurityProfile.DEFAULT,
                    Map.of(),
                    "/workspace/.output"
                )
            )
            .withMessageContaining("resourceLimits");
    }

    @Test
    @DisplayName("should accept valid spec with nullable fields")
    void shouldAcceptValidSpec() {
        // networkPolicy, securityProfile, inputFiles, outputPath can all be null
        var spec = new SandboxSpec(
            UUID.randomUUID(),
            "alpine:latest",
            null,
            null,
            null,
            ResourceLimits.DEFAULT,
            null,
            null,
            null
        );
        // No exception thrown
        assertThat(spec.jobId()).isNotNull();
    }

    @Nested
    @DisplayName("ResourceLimits validation")
    class ResourceLimitsValidation {

        @Test
        @DisplayName("should reject zero memoryBytes")
        void shouldRejectZeroMemory() {
            assertThatThrownBy(() -> new ResourceLimits(0, 2.0, 256, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryBytes");
        }

        @Test
        @DisplayName("should reject negative cpus")
        void shouldRejectNegativeCpus() {
            assertThatThrownBy(() -> new ResourceLimits(4L * 1024 * 1024 * 1024, -1.0, 256, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpus");
        }

        @Test
        @DisplayName("should reject zero pidsLimit")
        void shouldRejectZeroPids() {
            assertThatThrownBy(() -> new ResourceLimits(4L * 1024 * 1024 * 1024, 2.0, 0, Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pidsLimit");
        }

        @Test
        @DisplayName("should reject null maxRuntime")
        void shouldRejectNullMaxRuntime() {
            assertThatNullPointerException()
                .isThrownBy(() -> new ResourceLimits(4L * 1024 * 1024 * 1024, 2.0, 256, null))
                .withMessageContaining("maxRuntime");
        }

        @Test
        @DisplayName("should reject zero maxRuntime")
        void shouldRejectZeroMaxRuntime() {
            assertThatThrownBy(() -> new ResourceLimits(4L * 1024 * 1024 * 1024, 2.0, 256, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRuntime");
        }

        @Test
        @DisplayName("should accept DEFAULT constants")
        void shouldAcceptDefaults() {
            assertThat(ResourceLimits.DEFAULT.memoryBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
            assertThat(ResourceLimits.DEFAULT.cpus()).isEqualTo(2.0);
            assertThat(ResourceLimits.DEFAULT.pidsLimit()).isEqualTo(256);
            assertThat(ResourceLimits.DEFAULT.maxRuntime()).isEqualTo(Duration.ofMinutes(10));
        }
    }
}
