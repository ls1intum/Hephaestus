package de.tum.cit.aet.hephaestus.integration.slack.refs;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Behaviour + JPA-mapping shape contract for {@link SlackMessage}.
 *
 * <p>This is a unit test (no Spring context) — it exercises:
 * <ul>
 *   <li>the entity's mutator / soft-delete invariants</li>
 *   <li>the JPA annotation surface so a refactor that drops {@code @Entity},
 *       changes the table name, or removes the thread-index regresses to a
 *       hard test failure rather than a silent runtime regression.</li>
 * </ul>
 * Round-trip persistence against Postgres lives in the {@code SlackMessageRepositoryIntegrationTest}
 * suite (Testcontainers) — out of scope for the unit tier.
 */
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
    @DisplayName("softDelete is idempotent — first call sets timestamp, replay does not shift it")
    void softDeleteIdempotent() {
        SlackMessage msg = new SlackMessage(Mockito.mock(Connection.class), "T", "C", "1.0");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");

        msg.softDelete(first);
        assertThat(msg.getDeletedAt()).isEqualTo(first);
        assertThat(msg.isDeleted()).isTrue();

        msg.softDelete(second);
        assertThat(msg.getDeletedAt())
            .as("Replay must not overwrite the original tombstone timestamp")
            .isEqualTo(first);
    }

    @Test
    @DisplayName("Setters mutate optional metadata fields")
    void settersMutateOptionalFields() {
        SlackMessage msg = new SlackMessage(Mockito.mock(Connection.class), "T", "C", "1.0");
        msg.setText("hello");
        msg.setUserId("U999");
        msg.setThreadTs("1700000000.000050");
        msg.setBlocks("[{\"type\":\"section\"}]");
        assertThat(msg.getText()).isEqualTo("hello");
        assertThat(msg.getUserId()).isEqualTo("U999");
        assertThat(msg.getThreadTs()).isEqualTo("1700000000.000050");
        assertThat(msg.getBlocks()).isEqualTo("[{\"type\":\"section\"}]");
    }

    @Test
    @DisplayName("JPA mapping declares slack_message table with the natural-key unique constraint")
    void jpaMappingShape() {
        assertThat(SlackMessage.class.isAnnotationPresent(Entity.class)).isTrue();
        Table table = SlackMessage.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("slack_message");

        UniqueConstraint[] uniques = table.uniqueConstraints();
        assertThat(uniques).hasSize(1);
        assertThat(uniques[0].name()).isEqualTo("uq_slack_message");
        assertThat(Arrays.asList(uniques[0].columnNames()))
            .containsExactly("connection_id", "team_id", "channel_id", "ts");
    }

    @Test
    @DisplayName("JPA mapping declares the O(log n) thread-fetch index")
    void jpaMappingDeclaresThreadIndex() {
        Table table = SlackMessage.class.getAnnotation(Table.class);
        Index[] indexes = table.indexes();
        assertThat(indexes)
            .as("composite index for (connection_id, channel_id, ts DESC) is required for thread-fetch O(log n)")
            .anySatisfy(idx -> {
                assertThat(idx.name()).isEqualTo("ix_slack_message_thread");
                assertThat(idx.columnList()).contains("connection_id", "channel_id", "ts DESC");
            });
    }
}
