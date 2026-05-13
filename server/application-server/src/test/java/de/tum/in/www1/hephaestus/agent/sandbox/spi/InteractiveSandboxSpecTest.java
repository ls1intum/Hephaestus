package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InteractiveSandboxSpec")
class InteractiveSandboxSpecTest extends BaseUnitTest {

    private static InteractiveSandboxSpec spec(Map<String, String> env) {
        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            "u1",
            "w1",
            "ghcr.io/example/agent:latest",
            List.of("node", "/run.mjs"),
            env,
            new NetworkPolicy(true, null, null, null),
            new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofMinutes(1)),
            SecurityProfile.DEFAULT,
            Map.of(),
            Map.of()
        );
    }

    @ParameterizedTest(name = "accepts well-formed env key: {0}")
    @ValueSource(strings = { "FOO", "FOO_BAR", "_LEADING_UNDERSCORE", "x", "MIXED_case_42", "A0" })
    void acceptsValidKeys(String key) {
        assertThatNoException().isThrownBy(() -> spec(Map.of(key, "v")));
    }

    @ParameterizedTest(name = "rejects ill-formed env key: {0}")
    @ValueSource(
        strings = { "", " ", "0LEADING_DIGIT", "with space", "FOO-BAR", "FOO.BAR", "--privileged", "-it", "=val" }
    )
    void rejectsInvalidKeys(String key) {
        assertThatThrownBy(() -> spec(Map.of(key, "v")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid env var name");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("rejects NUL in env value")
    void rejectsNulInValue() {
        assertThatThrownBy(() -> spec(Map.of("FOO", "a\0b")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUL");
    }
}
