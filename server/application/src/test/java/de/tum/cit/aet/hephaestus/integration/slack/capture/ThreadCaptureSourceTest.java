package de.tum.cit.aet.hephaestus.integration.slack.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.chat.item.ConversationThreadProjection;
import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadEvents;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ThreadCaptureSourceTest extends BaseUnitTest {
    @Test
    void shouldWithholdSourceBytesWhenChannelConsentIsPausedAfterPinning() {
        var threads = mock(SlackThreadRepository.class);
        var projection = mock(SlackConversationProjector.class);
        var connections = mock(ConnectionService.class);
        var workspace = new Workspace();
        workspace.setId(7L);
        var connection = new Connection(
                workspace, IntegrationKind.SLACK, "T1", new ConnectionConfig.SlackConfig("T1", null, null, Set.of()));
        connection.setState(IntegrationState.ACTIVE);
        when(connections.findActiveConnections(7L, IntegrationKind.SLACK)).thenReturn(List.of(connection));
        var thread = new SlackThread();
        thread.setWorkspaceId(7L);
        thread.setSlackChannelId("C1");
        thread.setSlackThreadTs("1700000000.000000");
        when(threads.findByWorkspaceIdAndId(7L, 19L)).thenReturn(Optional.of(thread));
        when(projection.threadReadability(7L, "C1", "1700000000.000000"))
                .thenReturn(ConversationThreadProjection.ThreadReadability.CONSENT_NOT_ACTIVE);
        var item = new ItemRef(7L, 41L, ThreadEvents.THREAD, 19L, null, null);
        var snapshot = new ItemSnapshot(
                item,
                new ItemVersion("frozen", null),
                new IntegrationRef(IntegrationKind.SLACK, 7L, "T1"),
                Map.of(),
                List.of(),
                new ItemLabel(41L, "Thread", "thread", null, null, null, IntegrationKind.SLACK));
        var request = new CaptureRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, Set.of(ThreadCaptureSource.SOURCE), "2.0.0", 3);
        var captured = new ThreadCaptureSource(JsonMapper.builder().build(), projection, threads, connections)
                .capture(snapshot, request);
        assertThat(captured.files()).isEmpty();
        assertThat(captured.citableTexts()).isEmpty();
        assertThat(captured.completeness()).isEmpty();
        assertThat(captured.stateOverrides())
                .containsEntry(
                        ThreadCaptureSource.SOURCE,
                        new SourceCaptureState.Redacted(SourceAbsenceReason.CONSENT_NOT_ACTIVE));
    }
}
