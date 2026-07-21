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
 */
class DeprecatedLlmEnvVarStartupWarnerTest extends BaseUnitTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DeprecatedLlmEnvVarStartupWarner.class);
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

        new DeprecatedLlmEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages)
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.base-url").contains("AI models"))
            .anySatisfy(m -> assertThat(m).contains("hephaestus.worker.llm.api-key").contains("AI models"))
            .anySatisfy(m ->
                assertThat(m).contains("hephaestus.sandbox.llm-proxy.enabled").contains("no standalone enable flag")
            );
    }

    @Test
    void warnsOnlyForThePropertyThatIsSet() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("hephaestus.worker.llm.base-url", "https://api.anthropic.com");

        new DeprecatedLlmEnvVarStartupWarner(environment).warnOnRetiredProperties();

        List<String> messages = warnMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("hephaestus.worker.llm.base-url");
    }

    @Test
    void neverWarnsWhenNoneOfTheRetiredPropertiesAreSet() {
        MockEnvironment environment = new MockEnvironment();

        new DeprecatedLlmEnvVarStartupWarner(environment).warnOnRetiredProperties();

        assertThat(warnMessages()).isEmpty();
    }
}
