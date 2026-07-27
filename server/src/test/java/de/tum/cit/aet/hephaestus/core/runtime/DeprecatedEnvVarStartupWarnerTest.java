package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

class DeprecatedEnvVarStartupWarnerTest extends BaseUnitTest {

    private static final Path COMPOSE_APP = Path.of("..", "docker", "compose.app.yaml");

    /** The service that carries the one-release pass-through block; see the warner's javadoc. */
    private static final String FORWARDING_SERVICE = "services.application-server.environment.";

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

    /**
     * Resolution through the real {@link SystemEnvironmentPropertySource}, not a hand-set property on a
     * {@code MockEnvironment}. That distinction is the whole bug this class exists to prevent: a
     * {@code MockEnvironment} answers to whatever string you put in it, so it will happily confirm a
     * watch-list of dotted names that no environment variable on earth can satisfy.
     */
    private static Environment environmentWith(Map<String, Object> vars) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
            .getPropertySources()
            .replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, vars)
            );
        return environment;
    }

    @Test
    void warnsOnEachRetiredEnvVarThatIsStillSet() {
        Map<String, Object> vars = new LinkedHashMap<>();
        DeprecatedEnvVarStartupWarner.retiredEnvVarNames().forEach(name -> vars.put(name, "leftover"));

        new DeprecatedEnvVarStartupWarner(environmentWith(vars)).warnOnRetiredProperties();

        assertThat(warnMessages()).hasSize(DeprecatedEnvVarStartupWarner.retiredEnvVarNames().size());
        assertThat(warnMessages())
            .allSatisfy(message -> assertThat(message).contains("is set but no longer read"))
            .anySatisfy(message -> assertThat(message).contains("AGENT_NATS_ENABLED").contains("AGENT_ENABLED"))
            .anySatisfy(message ->
                assertThat(message).contains("AGENT_NATS_FETCH_BATCH_SIZE").contains("AGENT_CLAIM_BATCH_SIZE")
            )
            .anySatisfy(message ->
                assertThat(message).contains("HEPHAESTUS_WORKER_LLM_BASE_URL").contains("AI models")
            );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retiredEnvVars")
    void warnsExactlyOnceNamingOnlyTheVarThatIsSet(String envVar) {
        new DeprecatedEnvVarStartupWarner(environmentWith(Map.of(envVar, "leftover"))).warnOnRetiredProperties();

        assertThat(warnMessages()).hasSize(1);
        assertThat(warnMessages().get(0)).startsWith(envVar + " is set but no longer read");
    }

    /**
     * The one-release Compose pass-through hands every retired var to the container as {@code ${VAR:-}},
     * so on the overwhelmingly common deployment — nothing stale set — each arrives as an empty string.
     * Treating that as "set" would put six WARNs in every clean boot log and train operators to ignore
     * the line.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("retiredEnvVars")
    void treatsTheEmptyValueOfAnUnsetComposeForwardAsAbsent(String envVar) {
        new DeprecatedEnvVarStartupWarner(environmentWith(Map.of(envVar, ""))).warnOnRetiredProperties();

        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void neverWarnsWhenNoneOfTheRetiredVarsAreSet() {
        new DeprecatedEnvVarStartupWarner(environmentWith(Map.of())).warnOnRetiredProperties();

        assertThat(warnMessages()).isEmpty();
    }

    /**
     * The watch list holds environment-variable names, verbatim. A dotted property name here is
     * unfireable for any var that does not carry the {@code HEPHAESTUS_} prefix — which was true of
     * {@code AGENT_NATS_ENABLED}, {@code AGENT_NATS_MAX_ACK_PENDING} and
     * {@code AGENT_NATS_FETCH_BATCH_SIZE}, i.e. of most of the list.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("retiredEnvVars")
    void everyWatchedNameIsSpeltAsAnEnvironmentVariable(String name) {
        assertThat(name)
            .as("watch environment-variable names (FOO_BAR), never dotted property names (foo.bar)")
            .matches("[A-Z][A-Z0-9_]*");
    }

    /**
     * A Compose {@code .env} entry is an interpolation input, not container environment. Unless the
     * compose file names the var under {@code environment:}, the JVM never sees the operator's stale
     * line and the warner is decoration on every Compose deployment — which is what it was.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("retiredEnvVars")
    void everyWatchedVarIsForwardedToTheContainerByTheShippedComposeFile(String name) throws Exception {
        String forwarded = propertyIn(COMPOSE_APP, FORWARDING_SERVICE + name);

        assertThat(forwarded)
            .as(
                "docker/compose.app.yaml must forward %s to the container (as ${%s:-}) for the release " +
                    "in which it is warned about, or a stale docker/.env value never reaches the JVM",
                name,
                name
            )
            .isNotNull()
            .contains(name);
    }

    /**
     * A WARN has to mean "your leftover", so nothing shipped may still read the var. Checks the raw
     * text rather than parsed keys: the failure mode is a surviving {@code ${VAR:default}} placeholder,
     * which parses to an ordinary string and is invisible in the key set.
     *
     * <p>Static content, deliberately: booting each profile to check would need every one of them to
     * have a satisfiable environment, and would not fail until it did.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedProfileYamlFiles")
    void noShippedProfileStillReadsARetiredEnvVar(Path profile) throws Exception {
        String yaml = Files.readString(profile, StandardCharsets.UTF_8);

        List<String> stillRead = new ArrayList<>();
        for (String name : DeprecatedEnvVarStartupWarner.retiredEnvVarNames()) {
            if (yaml.contains("${" + name + ":") || yaml.contains("${" + name + "}")) {
                stillRead.add(name);
            }
        }

        assertThat(stillRead)
            .as("%s still reads a retired var — it is live config, not a leftover, so warning is wrong", profile)
            .isEmpty();
    }

    static List<String> retiredEnvVars() {
        return List.copyOf(DeprecatedEnvVarStartupWarner.retiredEnvVarNames());
    }

    static List<Path> shippedProfileYamlFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            return files
                .filter(path -> path.getFileName().toString().matches("application.*\\.yml"))
                .sorted()
                .toList();
        }
    }

    private static String propertyIn(Path yaml, String key) throws IOException {
        return new YamlPropertySourceLoader()
            .load(yaml.toString(), new FileSystemResource(yaml))
            .stream()
            .map(PropertySource.class::cast)
            .filter(source -> source.containsProperty(key))
            .findFirst()
            .map(source -> String.valueOf(source.getProperty(key)))
            .orElse(null);
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
