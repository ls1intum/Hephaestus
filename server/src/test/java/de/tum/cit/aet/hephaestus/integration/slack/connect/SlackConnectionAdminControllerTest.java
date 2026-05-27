package de.tum.cit.aet.hephaestus.integration.slack.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class SlackConnectionAdminControllerTest extends BaseUnitTest {

    @Mock
    private ConnectionService connectionService;

    @Mock
    private SlackMessageService slackMessageService;

    private SlackConnectionAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new SlackConnectionAdminController(connectionService, slackMessageService);
        when(connectionService.findSlackNotificationConfig(anyLong())).thenReturn(
            Optional.of(new ConnectionConfig.SlackConfig("T1", "Acme", "C123456789", "core-team", Set.of()))
        );
    }

    @Test
    void sendTestMessage_happyPath_returns200() {
        ResponseEntity<SlackTestMessageResponse> response = controller.sendTestMessage(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ok()).isTrue();
        assertThat(response.getBody().channelId()).isEqualTo("C123456789");
        assertThat(response.getBody().slackError()).isNull();
    }

    @Test
    void sendTestMessage_noActiveConnection_returns404() {
        when(connectionService.findSlackNotificationConfig(eq(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.sendTestMessage(2L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404")
            .hasMessageContaining("No ACTIVE Slack Connection");
    }

    @Test
    void sendTestMessage_noChannelConfigured_returns404() {
        when(connectionService.findSlackNotificationConfig(eq(3L))).thenReturn(
            Optional.of(new ConnectionConfig.SlackConfig("T1", "Acme", null, "core-team", Set.of()))
        );

        assertThatThrownBy(() -> controller.sendTestMessage(3L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404")
            .hasMessageContaining("channel not configured");
    }

    @ParameterizedTest
    @CsvSource(
        {
            "channel_not_found,400",
            "is_archived,400",
            "invalid_blocks,400",
            "invalid_arguments,400",
            "msg_too_long,400",
            "not_in_channel,403",
            "missing_scope,403",
            "cannot_dm_bot,403",
            "rate_limited,502",
            "internal_error,502",
            "transport_failure,502",
            "unknown_thing,502",
        }
    )
    void sendTestMessage_slackErrorMapsToStatus(String slackError, int expectedStatus) {
        doThrow(new SlackSendException(1L, "C123456789", slackError))
            .when(slackMessageService)
            .sendForWorkspace(anyLong(), anyString(), any(), anyString());

        ResponseEntity<SlackTestMessageResponse> response = controller.sendTestMessage(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ok()).isFalse();
        assertThat(response.getBody().channelId()).isEqualTo("C123456789");
        assertThat(response.getBody().slackError()).isEqualTo(slackError);
    }
}
