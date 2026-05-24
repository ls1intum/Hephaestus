package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@DisplayName("SlackMessageDeletionHandler — unit")
class SlackMessageDeletionHandlerTest extends BaseUnitTest {

    private static final Instant FIXED = Instant.parse("2026-05-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Mock private ConnectionRepository connectionRepository;
    @Mock private SlackMessageRepository messageRepository;

    private SlackMessageDeletionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackMessageDeletionHandler(connectionRepository, messageRepository, CLOCK);
    }

    @Test
    @DisplayName("Unknown team_id returns empty Optional, no repo write")
    void unknownTeam() {
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.SLACK, "T-gone"))
            .thenReturn(Optional.empty());

        Optional<Integer> result = handler.onMessageDeleted(1L, "T-gone", "C", "1.0");

        assertThat(result).isEmpty();
        verify(messageRepository, never()).softDelete(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("First delivery → 1 row tombstoned, workspace + clock-fixed timestamp")
    void firstDeliveryTombstonesOneRow() {
        Connection conn = Mockito.mock(Connection.class);
        when(conn.getId()).thenReturn(77L);
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.SLACK, "T1"))
            .thenReturn(Optional.of(conn));
        when(messageRepository.softDelete(1L, 77L, "C", "1.0", FIXED)).thenReturn(1);

        Optional<Integer> result = handler.onMessageDeleted(1L, "T1", "C", "1.0");

        assertThat(result).contains(1);
        verify(messageRepository).softDelete(1L, 77L, "C", "1.0", FIXED);
    }

    @Test
    @DisplayName("Replayed delivery → 0 rows (already tombstoned), still returns 0 not error")
    void replayedDeliveryIsNoop() {
        Connection conn = Mockito.mock(Connection.class);
        when(conn.getId()).thenReturn(77L);
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.SLACK, "T1"))
            .thenReturn(Optional.of(conn));
        when(messageRepository.softDelete(1L, 77L, "C", "1.0", FIXED)).thenReturn(0);

        Optional<Integer> result = handler.onMessageDeleted(1L, "T1", "C", "1.0");

        assertThat(result).contains(0);
    }
}
