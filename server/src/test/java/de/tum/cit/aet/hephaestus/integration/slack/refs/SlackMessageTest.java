package de.tum.cit.aet.hephaestus.integration.slack.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlackMessageTest extends BaseUnitTest {

    @Test
    void softDeleteIdempotentReplayPreservesOriginalTombstone() {
        SlackMessage msg = new SlackMessage(Mockito.mock(Connection.class), "T", "C", "1.0");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");

        msg.softDelete(first);
        msg.softDelete(second);

        assertThat(msg.isDeleted()).isTrue();
        assertThat(msg.getDeletedAt())
            .as("Replay must not overwrite the original tombstone timestamp")
            .isEqualTo(first);
    }
}
