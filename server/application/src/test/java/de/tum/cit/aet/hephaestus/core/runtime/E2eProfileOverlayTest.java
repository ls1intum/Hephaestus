package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class E2eProfileOverlayTest extends BaseUnitTest {

    private static final String OVERLAY = "application-e2e.yml";

    @Test
    void enablesLocalJobExecution() throws IOException {
        assertThat(overlay().getProperty("hephaestus.agent.enabled")).isEqualTo(true);
    }

    @Test
    void usesLocalNatsAndUnfilteredSync() throws IOException {
        PropertySource<?> overlay = overlay();

        assertThat(overlay.getProperty("hephaestus.sync.nats.server")).isEqualTo("nats://localhost:4222");
        assertThat(overlay.getProperty("hephaestus.sync.filters.allowed-organizations"))
                .isEqualTo("");
    }

    private static PropertySource<?> overlay() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(OVERLAY, new ClassPathResource(OVERLAY));
        assertThat(sources).hasSize(1);
        return sources.get(0);
    }
}
