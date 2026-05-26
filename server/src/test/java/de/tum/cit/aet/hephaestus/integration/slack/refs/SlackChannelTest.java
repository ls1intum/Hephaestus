package de.tum.cit.aet.hephaestus.integration.slack.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("SlackChannel")
class SlackChannelTest extends BaseUnitTest {

    @Test
    @DisplayName("Constructor wires connection + channel id")
    void constructor() {
        Connection conn = Mockito.mock(Connection.class);
        SlackChannel ch = new SlackChannel(conn, "C123", "general");
        assertThat(ch.getConnection()).isSameAs(conn);
        assertThat(ch.getChannelId()).isEqualTo("C123");
        assertThat(ch.getName()).isEqualTo("general");
        assertThat(ch.isArchived()).isFalse();
        assertThat(ch.getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("archive is idempotent — replay preserves the original timestamp")
    void archiveIdempotent() {
        SlackChannel ch = new SlackChannel(Mockito.mock(Connection.class), "C", null);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        ch.archive(first);
        ch.archive(second);
        assertThat(ch.isArchived()).isTrue();
        assertThat(ch.getArchivedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("unarchive clears the timestamp")
    void unarchive() {
        SlackChannel ch = new SlackChannel(Mockito.mock(Connection.class), "C", null);
        ch.archive(Instant.now());
        ch.unarchive();
        assertThat(ch.isArchived()).isFalse();
        assertThat(ch.getArchivedAt()).isNull();
    }
}
