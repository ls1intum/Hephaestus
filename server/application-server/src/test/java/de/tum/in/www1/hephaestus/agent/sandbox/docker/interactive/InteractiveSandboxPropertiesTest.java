package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

@DisplayName("InteractiveSandboxProperties")
class InteractiveSandboxPropertiesTest extends BaseUnitTest {

    private static InteractiveSandboxProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source)).bindOrCreate(
            "hephaestus.mentor",
            InteractiveSandboxProperties.class
        );
    }

    @Test
    @DisplayName("@DefaultValue annotations bind to the documented defaults from an empty source")
    void bindsDefaultsFromEmptyMap() {
        InteractiveSandboxProperties p = bind(Map.of());
        assertThat(p.idleTtlSeconds()).isEqualTo(300);
        assertThat(p.graceTimeoutSeconds()).isEqualTo(25);
        assertThat(p.reapIntervalSeconds()).isEqualTo(30);
        assertThat(p.ringBufferFrames()).isEqualTo(512);
        assertThat(p.stdinWriteTimeoutMs()).isEqualTo(5000);
        assertThat(p.sendQueueCapacity()).isEqualTo(64);
        assertThat(p.subscriberQueueCapacity()).isEqualTo(64);
        assertThat(p.attachFirstFrameTimeoutSeconds()).isEqualTo(30);
        assertThat(p.maxSessionsPerUser()).isEqualTo(3);
        assertThat(p.maxSessionsTotal()).isEqualTo(50);
        assertThat(p.maxFrameChars()).isEqualTo(1_048_576);
    }

    @Test
    @DisplayName("overrides bind from a real config source (proves the prefix wiring works end-to-end)")
    void bindsOverridesFromSource() {
        InteractiveSandboxProperties p = bind(
            Map.of("hephaestus.mentor.idle-ttl-seconds", "60", "hephaestus.mentor.max-frame-chars", "16384")
        );
        assertThat(p.idleTtlSeconds()).isEqualTo(60);
        assertThat(p.maxFrameChars()).isEqualTo(16_384);
        assertThat(p.ringBufferFrames()).isEqualTo(512);
    }
}
