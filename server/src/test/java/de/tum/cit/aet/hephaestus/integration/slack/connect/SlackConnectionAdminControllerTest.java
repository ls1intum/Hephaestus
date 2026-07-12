package de.tum.cit.aet.hephaestus.integration.slack.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.HealthVisibility;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

class SlackConnectionAdminControllerTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    @Mock
    private SlackMessageService slackMessageService;

    private SlackConnectionAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new SlackConnectionAdminController(connectionService, slackMessageService);
        // lenient: the channel-override test deliberately never reads the persisted config.
        lenient()
            .when(connectionService.findSlackNotificationConfig(anyLong()))
            .thenReturn(
                Optional.of(new ConnectionConfig.SlackConfig("T1", "Acme", "C123456789", "core-team", null, Set.of()))
            );
    }

    @Test
    void sendTestMessage_happyPath_usesPersistedChannel() {
        SlackTestMessageResponseDTO result = controller.sendTestMessage(ctx(1L), null);

        assertThat(result.ok()).isTrue();
        assertThat(result.channelId()).isEqualTo("C123456789");
        assertThat(result.slackError()).isNull();
    }

    @Test
    void sendTestMessage_overrideChannel_testsTypedChannelWithoutReadingPersisted() {
        SlackTestMessageResponseDTO result = controller.sendTestMessage(
            ctx(1L),
            new SlackTestMessageRequestDTO("C999999999")
        );

        assertThat(result.ok()).isTrue();
        assertThat(result.channelId()).isEqualTo("C999999999");
        // A non-blank override short-circuits the persisted lookup entirely.
        verify(connectionService, never()).findSlackNotificationConfig(anyLong());
    }

    @Test
    void sendTestMessage_blankOverride_fallsBackToPersistedChannel() {
        SlackTestMessageResponseDTO result = controller.sendTestMessage(ctx(1L), new SlackTestMessageRequestDTO("   "));

        assertThat(result.ok()).isTrue();
        assertThat(result.channelId()).isEqualTo("C123456789");
    }

    @Test
    void sendTestMessage_noChannelAnywhere_returnsProbeFailureNotError() {
        when(connectionService.findSlackNotificationConfig(eq(2L))).thenReturn(Optional.empty());

        SlackTestMessageResponseDTO result = controller.sendTestMessage(ctx(2L), null);

        assertThat(result.ok()).isFalse();
        assertThat(result.channelId()).isNull();
        assertThat(result.slackError()).isEqualTo("no_channel_configured");
        verify(slackMessageService, never()).sendForWorkspace(anyLong(), anyString(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "channel_not_found", "not_in_channel", "missing_scope", "rate_limited", "internal_error" })
    void sendTestMessage_slackRejection_isProbeFailureCarryingTheErrorCode(String slackError) {
        doThrow(new SlackSendException(1L, "C123456789", slackError))
            .when(slackMessageService)
            .sendForWorkspace(anyLong(), anyString(), any(), anyString());

        SlackTestMessageResponseDTO result = controller.sendTestMessage(ctx(1L), null);

        assertThat(result.ok()).isFalse();
        assertThat(result.channelId()).isEqualTo("C123456789");
        assertThat(result.slackError()).isEqualTo(slackError);
    }

    private static WorkspaceContext ctx(long workspaceId) {
        return new WorkspaceContext(
            workspaceId,
            "ws-" + workspaceId,
            "Workspace " + workspaceId,
            AccountType.ORG,
            null,
            false,
            false,
            HealthVisibility.MENTORS_ONLY,
            Set.of(WorkspaceRole.ADMIN)
        );
    }
}
