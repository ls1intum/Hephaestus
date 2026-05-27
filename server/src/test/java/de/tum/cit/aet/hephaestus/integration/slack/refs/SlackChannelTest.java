package de.tum.cit.aet.hephaestus.integration.slack.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlackChannelTest extends BaseUnitTest {

    @Test
    void archiveIdempotentReplayPreservesOriginalTimestamp() {
        SlackChannel ch = new SlackChannel(Mockito.mock(Connection.class), "C", null);
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        ch.archive(first);
        ch.archive(second);
        assertThat(ch.isArchived()).isTrue();
        assertThat(ch.getArchivedAt()).isEqualTo(first);
    }

    @Test
    void unarchiveClearsTimestamp() {
        SlackChannel ch = new SlackChannel(Mockito.mock(Connection.class), "C", null);
        ch.archive(Instant.now());
        ch.unarchive();
        assertThat(ch.isArchived()).isFalse();
        assertThat(ch.getArchivedAt()).isNull();
    }
}
