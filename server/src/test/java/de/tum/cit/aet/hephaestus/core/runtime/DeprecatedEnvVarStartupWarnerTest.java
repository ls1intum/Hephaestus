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

        new DeprecatedEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(5);
        assertThat(messages)
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.base-url").contains("AI models"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.api-key").contains("AI models"))
            .anySatisfy(m ->
                assertThat(m).contains("hephaestus.sandbox.llm-proxy.enabled").contains("no standalone enable flag")
            )
            .anySatisfy(m -> assertThat(m).contains("hephaestus.agent.nats.enabled").contains("AGENT_ENABLED"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.agent.nats.server").contains("AGENT_ENABLED"));
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
}
