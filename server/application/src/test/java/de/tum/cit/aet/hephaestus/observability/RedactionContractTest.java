package de.tum.cit.aet.hephaestus.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
class RedactionContractTest {

    @Test
    void shouldRedactKnownCredentialFormatsFromConfiguredEncoder() throws JoranException {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(((LoggerContext) LoggerFactory.getILoggerFactory()).getMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(
                Objects.requireNonNull(RedactionContractTest.class.getResource("/logback-spring.xml")));

        String line;
        try {
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            assertEquals(Level.INFO, root.getLevel());
            ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root.getAppender("CONSOLE");
            Logger logger = context.getLogger(RedactionContractTest.class);
            LoggingEvent event = new LoggingEvent();
            event.setLoggerName(logger.getName());
            event.setLoggerContext(context);
            event.setLevel(Level.INFO);
            event.setInstant(Instant.now());
            event.setMDCPropertyMap(Map.of("workspace.id", "42", "token", "structured-secret", "level", "FORGED"));
            event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("Bearer exception-secret")));
            event.setMessage("credentials: Bearer eyJhbGciOi.test glpat-secret_123 "
                    + "https://example.test/cb?token=url-secret&x=1 github_pat_secret_123");
            line = new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);
        } finally {
            context.stop();
        }
        JsonNode json = new ObjectMapper().readTree(line);
        String message = json.get("message").asString();
        assertTrue(message.contains("Bearer ***"), line);
        assertTrue(message.contains("glpat-***"));
        assertTrue(message.contains("?token=***&x=1"));
        assertTrue(message.contains("github_pat_***"));
        assertFalse(line.contains("eyJhbGciOi"));
        assertFalse(line.contains("secret_123"));
        assertFalse(line.contains("structured-secret"));
        assertFalse(line.contains("exception-secret"));
        assertTrue(json.has("timestamp"));
        assertTrue(json.has("logger"));
        assertTrue(json.has("stacktrace"));
        assertEquals("INFO", json.get("level").asString());
        assertEquals("42", json.get("mdc").get("workspace.id").asString());
        assertEquals("***", json.get("mdc").get("token").asString());
        assertEquals("FORGED", json.get("mdc").get("level").asString());
        assertTrue(line.endsWith(System.lineSeparator()));
        assertFalse(line.substring(0, line.length() - System.lineSeparator().length())
                .contains(System.lineSeparator()));
    }
}
