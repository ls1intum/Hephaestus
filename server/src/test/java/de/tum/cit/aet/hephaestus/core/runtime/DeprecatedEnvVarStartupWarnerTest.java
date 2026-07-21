package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * #1368 fix wave: {@code HEPHAESTUS_WORKER_LLM_BASE_URL} / {@code HEPHAESTUS_WORKER_LLM_API_KEY} /
 * {@code HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED} are now silently ignored (see MIGRATION.md). This
 * warner is the operator-visible signal that a deployment is still setting one of them.
 *
 * <p>#1368 NATS→Postgres cutover: {@code AGENT_NATS_ENABLED} / {@code hephaestus.agent.nats.server}
 * joined the retired list — the agent job queue is now the {@code agent_job} table.
 */
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

    @Test
    void warnsOnlyForThePropertyThatIsSet() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("hephaestus.worker.llm.base-url", "https://api.anthropic.com");

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("hephaestus.worker.llm.base-url");
    }

    @Test
    void neverWarnsWhenNoneOfTheRetiredPropertiesAreSet() {
        MockEnvironment environment = new MockEnvironment();

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void warnsWhenAgentNatsEnabledIsStillSet() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("hephaestus.agent.nats.enabled", "false");

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("hephaestus.agent.nats.enabled").contains("PostgreSQL");
    }

    /**
     * #1368 fix wave: {@code application-worker.yml} used to set {@code hephaestus.agent.nats.enabled}
     * and the (also-retired) {@code hephaestus.worker.llm.base-url}/{@code api-key} via {@code
     * ${VAR:}}-style placeholders with an EMPTY-STRING default. Spring's YAML property source binds
     * the key regardless — an unset env var resolves to {@code ""}, not "absent" — so {@link
     * DeprecatedEnvVarStartupWarner#warnOnRetiredProperties()} (which checks {@code getProperty(...) !=
     * null}) fired a false-positive WARN on every single worker boot, even when the operator never
     * touched any of those env vars. This is a static-content regression guard, not an env/property
     * simulation: it loads the actual shipped profile and asserts none of the currently-retired keys
     * are defined in it any more.
     */
    @Test
    void workerProfileNoLongerDefinesAnyRetiredKey() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
            "application-worker.yml",
            new ClassPathResource("application-worker.yml")
        );

        List<String> retiredKeysStillDefined = sources
            .stream()
            .flatMap(source ->
                java.util.Arrays.stream(
                    ((org.springframework.core.env.EnumerablePropertySource<?>) source).getPropertyNames()
                )
            )
            .filter(
                name ->
                    name.equals("hephaestus.agent.nats.enabled") ||
                    name.equals("hephaestus.agent.nats.server") ||
                    name.equals("hephaestus.agent.nats.max-ack-pending") ||
                    name.equals("hephaestus.agent.nats.fetch-batch-size") ||
                    name.equals("hephaestus.worker.llm.base-url") ||
                    name.equals("hephaestus.worker.llm.api-key")
            )
            .toList();

        assertThat(retiredKeysStillDefined)
            .as(
                "application-worker.yml must not (re-)define retired properties — even an empty-string " +
                    "placeholder default makes DeprecatedEnvVarStartupWarner false-positive on every worker boot"
            )
            .isEmpty();
    }

    /**
     * #1368 fix wave (finding 1 — worker can't serve the LLM proxy): {@code application-worker.yml}
     * used to set {@code server.port: -1}, which disables the HTTP connector entirely. Since
     * {@code LlmProxyController}/{@code LlmProxySecurityConfig} wire on this exact profile whenever
     * {@code hephaestus.agent.enabled=true} (the job-execution capability gate, ADR 0006), that made
     * the proxy unreachable on every worker pod that actually executes jobs. This regression guard
     * loads the shipped YAML directly (no Spring context — {@code webEnvironment=RANDOM_PORT} test
     * infra would force a real port regardless of the YAML default, masking a regression here) and
     * asserts the unresolved placeholder default is no longer the disabled-connector sentinel.
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
