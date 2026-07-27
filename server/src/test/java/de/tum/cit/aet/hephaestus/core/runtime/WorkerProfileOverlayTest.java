package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads {@code application-worker.yml} itself. Every other role test asserts which beans wire under a
 * hand-written property set; the two values below are regressions that a hand-written property set
 * cannot catch, because the mistake would be IN the file the deployment loads and no test that sets
 * its own properties ever reads it.
 */
class WorkerProfileOverlayTest extends BaseUnitTest {

    private static final String OVERLAY = "application-worker.yml";

    private static PropertySource<?> workerOverlay() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(OVERLAY, new ClassPathResource(OVERLAY));
        assertThat(sources).as("the overlay must be a single YAML document").hasSize(1);
        return sources.get(0);
    }

    /**
     * The LLM proxy is the only path a sandbox has to a provider key (ADR 0006), and it is served by
     * this pod's own HTTP connector. {@code server.port: -1} disables that connector: the beans still
     * wire, so every context test stays green, while the sandbox's
     * {@code http://<pod>:<port>/internal/llm} target resolves to a negative port and every job this
     * worker claims fails to reach its model.
     */
    @Test
    void servesTheLlmProxyOnARealPort() throws IOException {
        assertThat(workerOverlay().getProperty("server.port")).isEqualTo("${SERVER_PORT:8080}");
    }

    /**
     * A worker inherits {@code AGENT_ENABLED} from the base configuration, where it is off. Pinning it
     * on here would make a pod started outside compose — with none of the env vars the operator was
     * told to set — claim jobs and spend LLM budget the operator believed inert. Compose passes the
     * variable explicitly, so only a non-compose deployment could ever have hit that.
     */
    @Test
    void doesNotTurnJobExecutionOnByItself() throws IOException {
        assertThat(workerOverlay().getProperty("hephaestus.agent.enabled")).isNull();
        assertThat(baseValueOf("hephaestus.agent.enabled")).isEqualTo("${AGENT_ENABLED:false}");
    }

    private static Object baseValueOf(String key) throws IOException {
        return new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"))
            .stream()
            .map(source -> source.getProperty(key))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
