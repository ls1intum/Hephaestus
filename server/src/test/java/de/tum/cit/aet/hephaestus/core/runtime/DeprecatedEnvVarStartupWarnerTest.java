package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class DeprecatedEnvVarStartupWarnerTest extends BaseUnitTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DeprecatedEnvVarStartupWarner.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<String> warnMessages() {
        return appender.list
            .stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    @Test
    void warnsOnEachRetiredPropertyThatIsStillSet() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("hephaestus.worker.llm.base-url", "https://api.anthropic.com");
        environment.setProperty("hephaestus.worker.llm.api-key", "sk-test");
        environment.setProperty("hephaestus.sandbox.llm-proxy.enabled", "true");
        environment.setProperty("hephaestus.agent.nats.enabled", "true");
        environment.setProperty("hephaestus.agent.nats.server", "nats://localhost:4222");
        environment.setProperty("hephaestus.agent.nats.max-ack-pending", "16");
        environment.setProperty("hephaestus.agent.nats.fetch-batch-size", "5");

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(7);
        assertThat(messages)
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.base-url").contains("AI models"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.api-key").contains("AI models"))
            .anySatisfy(m ->
                assertThat(m).contains("hephaestus.sandbox.llm-proxy.enabled").contains("no standalone enable flag")
            )
            .anySatisfy(m -> assertThat(m).contains("hephaestus.agent.nats.enabled").contains("AGENT_ENABLED"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.agent.nats.server").contains("AGENT_ENABLED"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.agent.nats.max-ack-pending").contains("PostgreSQL"))
            .anySatisfy(m ->
                assertThat(m).contains("hephaestus.agent.nats.fetch-batch-size").contains("AGENT_CLAIM_BATCH_SIZE")
            );
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        {
            "hephaestus.worker.llm.base-url, https://api.anthropic.com, AI models",
            "hephaestus.agent.nats.enabled, false, PostgreSQL",
        }
    )
    void warnsExactlyOnceNamingOnlyThePropertyThatIsSet(String property, String value, String guidance) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(property, value);

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains(property).contains(guidance);
    }

    @Test
    void neverWarnsWhenNoneOfTheRetiredPropertiesAreSet() {
        MockEnvironment environment = new MockEnvironment();

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        assertThat(warnMessages()).isEmpty();
    }

    /**
     * Spring's YAML source binds the key whatever the value is — a {@code ${VAR:}} placeholder with an
     * empty default resolves to {@code ""}, not "absent" — so the value cannot rescue a retired key;
     * the line has to be gone.
     *
     * <p>Static content, deliberately: booting each profile to check would need every one of them to
     * have a satisfiable environment, and would not fail until it did.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedProfileYamlFiles")
    void noShippedProfileDefinesARetiredProperty(Path profile) throws Exception {
        List<String> retiredKeysStillDefined = propertyNamesIn(profile)
            .stream()
            .filter(DeprecatedEnvVarStartupWarner.retiredPropertyNames()::contains)
            .toList();

        assertThat(retiredKeysStillDefined)
            .as("%s must not define retired properties — every boot of that profile would warn", profile.getFileName())
            .isEmpty();
    }

    static List<Path> shippedProfileYamlFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            return files
                .filter(path -> path.getFileName().toString().matches("application.*\\.yml"))
                .sorted()
                .toList();
        }
    }

    private static List<String> propertyNamesIn(Path profile) throws IOException {
        return new YamlPropertySourceLoader()
            .load(profile.toString(), new FileSystemResource(profile))
            .stream()
            .map(EnumerablePropertySource.class::cast)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
    }

    /**
     * {@code server.port: -1} disables the HTTP connector entirely, which makes the LLM proxy unreachable
     * on every worker pod that executes jobs (ADR 0006). Reads the shipped YAML rather than booting a
     * context: {@code webEnvironment=RANDOM_PORT} forces a real port and would mask the regression.
     */
    @Test
    void workerProfileServerPortIsNotTheDisabledConnectorSentinel() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
            "application-worker.yml",
            new ClassPathResource("application-worker.yml")
        );

        String resolvedPort = sources
            .stream()
            .filter(source -> source.containsProperty("server.port"))
            .findFirst()
            .map(source -> String.valueOf(source.getProperty("server.port")))
            .orElseThrow(() -> new AssertionError("application-worker.yml no longer sets server.port at all"));

        assertThat(resolvedPort)
            .as(
                "server.port must not be the disabled-HTTP-connector sentinel (-1) — the LLM proxy " +
                    "must be reachable on any pod that executes jobs"
            )
            .doesNotContain("-1");
    }
}
