package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.refs.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener.ScopeDelta;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@DisplayName("SlackLifecycleListener — unit")
class SlackLifecycleListenerTest extends BaseUnitTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private SlackChannelRepository channelRepository;

    @Mock
    private SlackMessageRepository messageRepository;

    private SlackLifecycleListener listener;

    @BeforeEach
    void setUp() {
        listener = new SlackLifecycleListener(connectionRepository, channelRepository, messageRepository);
    }

    @Test
    @DisplayName("onScopeChanged with empty removedExternalIds is a no-op (no repo lookups)")
    void emptyDeltaIsNoOp() {
        listener.onScopeChanged(
            new IntegrationRef(IntegrationKind.SLACK, 1L, "T1"),
            new ScopeDelta(List.of(), List.of())
        );
        verify(connectionRepository, never()).findByWorkspaceIdAndKindAndInstanceKey(anyLong(), any(), anyString());
        verify(channelRepository, never()).deleteByConnectionIdAndChannelIdIn(anyLong(), any());
    }

    @Test
    @DisplayName("onScopeChanged with unknown connection logs + skips (no exception)")
    void unknownConnectionIsSilent() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.SLACK, 1L, "T-unknown");
        when(
            connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.SLACK, "T-unknown")
        ).thenReturn(Optional.empty());

        listener.onScopeChanged(ref, new ScopeDelta(List.of(), List.of("C-gone")));

        verify(channelRepository, never()).deleteByConnectionIdAndChannelIdIn(anyLong(), any());
        verify(messageRepository, never()).deleteByConnectionIdAndChannelId(anyLong(), anyString());
    }

    @Test
    @DisplayName("onScopeChanged with removed channel deletes messages first, then channel row")
    void removalCascadesMessagesThenChannel() {
        IntegrationRef ref = new IntegrationRef(IntegrationKind.SLACK, 1L, "T1");
        Connection connection = Mockito.mock(Connection.class);
        when(connection.getId()).thenReturn(77L);
        when(connectionRepository.findByWorkspaceIdAndKindAndInstanceKey(1L, IntegrationKind.SLACK, "T1")).thenReturn(
            Optional.of(connection)
        );
        when(messageRepository.deleteByConnectionIdAndChannelId(77L, "C-removed")).thenReturn(12);
        when(channelRepository.deleteByConnectionIdAndChannelIdIn(eq(77L), any())).thenReturn(1);

        listener.onScopeChanged(ref, new ScopeDelta(List.of(), List.of("C-removed")));

        verify(messageRepository, times(1)).deleteByConnectionIdAndChannelId(77L, "C-removed");
        verify(channelRepository, times(1)).deleteByConnectionIdAndChannelIdIn(77L, List.of("C-removed"));
    }
}
