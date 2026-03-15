package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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
        assert spec.jobId() != null;
    }
}
