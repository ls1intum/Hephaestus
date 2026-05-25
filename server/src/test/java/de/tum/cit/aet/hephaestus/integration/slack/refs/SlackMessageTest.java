package de.tum.cit.aet.hephaestus.integration.slack.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("SlackMessage")
class SlackMessageTest extends BaseUnitTest {

    @Test
    @DisplayName("Constructor wires connection + identity tuple, leaves optional fields null")
    void constructorWiresIdentityTuple() {
        Connection conn = Mockito.mock(Connection.class);
        SlackMessage msg = new SlackMessage(conn, "T123", "C456", "1700000000.000100");

        assertThat(msg.getConnection()).isSameAs(conn);
        assertThat(msg.getTeamId()).isEqualTo("T123");
        assertThat(msg.getChannelId()).isEqualTo("C456");
        assertThat(msg.getTs()).isEqualTo("1700000000.000100");
        assertThat(msg.getText()).isNull();
        assertThat(msg.getDeletedAt()).isNull();
        assertThat(msg.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("softDelete is idempotent — replay does not shift the tombstone")
    void softDeleteIdempotent() {
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
