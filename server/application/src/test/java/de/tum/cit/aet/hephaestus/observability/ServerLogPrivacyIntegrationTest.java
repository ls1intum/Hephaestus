package de.tum.cit.aet.hephaestus.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Framework boundaries that write request or row content before application code sees the failure. */
class ServerLogPrivacyIntegrationTest extends BaseIntegrationTest {

    private static final String TOMCAT_PARSER = "org.apache.coyote.http11.Http11Processor";

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private record Rejection(String sqlState, String message) {}

    @Test
    void shouldKeepRejectedRowValuesOutOfTheDriverMessage() {
        Rejection rejection = Objects.requireNonNull(jdbc.execute((ConnectionCallback<Rejection>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE log_privacy_canary (email text"
                        + " CONSTRAINT uq_log_privacy_canary UNIQUE)");
                try {
                    statement.execute("INSERT INTO log_privacy_canary VALUES ('private-row-canary@example.test')");
                    statement.execute("INSERT INTO log_privacy_canary VALUES ('private-row-canary@example.test')");
                    throw new IllegalStateException("the second insert was not rejected");
                } catch (SQLException duplicate) {
                    return new Rejection(
                            Objects.requireNonNull(duplicate.getSQLState()),
                            Objects.requireNonNull(duplicate.getMessage()));
                } finally {
                    statement.execute("DROP TABLE log_privacy_canary");
                }
            }
        }));

        assertThat(rejection.sqlState()).isEqualTo("23505");
        assertThat(rejection.message()).contains("uq_log_privacy_canary").doesNotContain("private-row-canary");
    }

    @Test
    void shouldKeepTheRawTargetOfAnUnparseableRequestOutOfTheLog() throws IOException {
        Logger parser = (Logger) LoggerFactory.getLogger(TOMCAT_PARSER);
        Level shipped = parser.getLevel();
        assertThat(shipped).as("application.yml pins " + TOMCAT_PARSER).isEqualTo(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        parser.addAppender(appender);
        try {
            assertThat(sendUnparseableRequest()).startsWith("HTTP/1.1 400");
            assertThat(appender.list).isEmpty();

            // Unbounded, the same logger receives the target: the pinned name is the one Tomcat uses.
            parser.setLevel(Level.DEBUG);
            assertThat(sendUnparseableRequest()).startsWith("HTTP/1.1 400");
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getThrowableProxy())
                            .isNotNull()
                            .satisfies(thrown -> assertThat(thrown.getMessage()).contains("private-token-canary")));
        } finally {
            parser.setLevel(shipped);
            parser.detachAppender(appender);
            appender.stop();
        }
    }

    private String sendUnparseableRequest() throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /unsubscribe?token=private-token-canary{} HTTP/1.1\r\n"
                            + "Host: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
}
