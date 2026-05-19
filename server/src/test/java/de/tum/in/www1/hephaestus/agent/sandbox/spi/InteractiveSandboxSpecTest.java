package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

    @ParameterizedTest(name = "rejects unsafe control char in env value: {0}")
    @ValueSource(strings = { "a\nb", "a\rb" })
    void rejectsLfCrInValue(String value) {
        assertThatThrownBy(() -> spec(Map.of("FOO", value)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUL/LF/CR");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("environment map is defensively copied — post-construction mutation does not affect the spec")
    void environmentIsCopied() {
        Map<String, String> env = new HashMap<>();
        env.put("OK_KEY", "good");
        InteractiveSandboxSpec s = spec(env);
        // Mutate caller's map after validation.
        env.put("LD_PRELOAD", "/tmp/evil.so");
        env.remove("OK_KEY");
        assertThat(s.environment()).containsEntry("OK_KEY", "good").doesNotContainKey("LD_PRELOAD");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("command list is defensively copied — post-construction mutation does not affect the spec")
    void commandIsCopied() {
        List<String> cmd = new ArrayList<>(List.of("node", "/run.mjs"));
        InteractiveSandboxSpec s = new InteractiveSandboxSpec(
            UUID.randomUUID(),
            "u1",
            "w1",
            "ghcr.io/example/agent:latest",
            cmd,
            Map.of(),
            new NetworkPolicy(true, null, null, null),
            new ResourceLimits(256 * 1024 * 1024, 0.5, 64, Duration.ofMinutes(1)),
            SecurityProfile.DEFAULT,
            Map.of(),
            Map.of()
        );
        cmd.set(0, "/bin/sh");
        cmd.add("-c");
        cmd.add("rm -rf /");
        assertThat(s.command()).containsExactly("node", "/run.mjs");
    }
}
