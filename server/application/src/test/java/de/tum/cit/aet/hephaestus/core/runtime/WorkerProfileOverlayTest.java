package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads {@code application-worker.yml} itself, and refreshes a context over it: these are regressions
 * no test that sets its own properties can catch, because the mistake would be IN the file the
 * deployment loads.
 */
class WorkerProfileOverlayTest extends BaseUnitTest {

    private static final String OVERLAY = "application-worker.yml";

    private static PropertySource<?> workerOverlay() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(OVERLAY, new ClassPathResource(OVERLAY));
        assertThat(sources).as("the overlay must be a single YAML document").hasSize(1);
        return sources.get(0);
    }

    /**
     * A sandbox reaches the worker on the gateway connector alone; the pod's own connector answers the
     * actuator probe and the management endpoints, and must not answer anything from a job network.
     */
    @Test
    void bindsAdministrativeHttpToLoopback() throws IOException {
        assertThat(InetAddress.getByName(resolvedOverlayValue("server.address")))
                .matches(InetAddress::isLoopbackAddress);
    }

    /**
     * The shipped topology gives {@code SERVER_PORT} and {@code MANAGEMENT_PORT} the same default, so the
     * management endpoints share the pod's one connector, and Spring Boot then rejects any
     * management-specific address or SSL setting. It rejects them from a {@code SmartInitializingSingleton}
     * — after every condition has matched — so nothing short of a refreshed context catches an overlay that
     * cannot boot. The ports are pinned equal here rather than left to resolve: the build runs with
     * {@code SERVER_PORT=0 MANAGEMENT_PORT=0}, and two zeroes are the one pair Boot reads as separate ports.
     */
    @Test
    void bootsWhenTheManagementEndpointsShareTheWorkerConnector() throws IOException {
        PropertySource<?> overlay = workerOverlay();
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EndpointAutoConfiguration.class,
                        WebEndpointAutoConfiguration.class,
                        ManagementContextAutoConfiguration.class))
                .withInitializer(
                        context -> context.getEnvironment().getPropertySources().addLast(overlay))
                .withPropertyValues("server.port=8080", "management.server.port=8080")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * The worker's own connector still has to be a real port — the actuator probe answers there, and
     * {@code server.port: -1} would disable it while every bean stays wired and every context test
     * stays green.
     */
    @Test
    void servesTheWorkerOnARealPort() throws IOException {
        assertThat(Integer.parseInt(resolvedOverlayValue("server.port"))).isPositive();
    }

    /**
     * A worker inherits {@code AGENT_ENABLED} from the base configuration, where it is off. Pinning it on
     * in the overlay would make a pod started outside compose — with none of the env vars the operator was
     * told to set — claim jobs and spend LLM budget the operator believed inert.
     */
    @Test
    void doesNotTurnJobExecutionOnByItself() throws IOException {
        assertThat(workerOverlay().getProperty("hephaestus.agent.enabled")).isNull();
        assertThat(baseValueOf("hephaestus.agent.enabled")).isEqualTo("${AGENT_ENABLED:false}");
    }

    /** The value a worker boots with when the operator sets no environment variable for it. */
    private static String resolvedOverlayValue(String key) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(workerOverlay());
        return Objects.requireNonNull(
                new PropertySourcesPropertyResolver(sources).getProperty(key), () -> key + " is not set by " + OVERLAY);
    }

    private static @Nullable Object baseValueOf(String key) throws IOException {
        return new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).stream()
                        .map(source -> source.getProperty(key))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
    }
}
