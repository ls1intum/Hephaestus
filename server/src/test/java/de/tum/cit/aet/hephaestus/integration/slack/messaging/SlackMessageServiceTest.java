package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SlackMessageServiceTest extends BaseUnitTest {

    @Mock
    private SlackCredentialProvider credentialProvider;

    private SlackMessageService service;

    @BeforeEach
    void setUp() {
        service = new SlackMessageService(credentialProvider);
    }

    @Test
    void sendForWorkspace_noToken_throwsSlackSendException() {
        when(credentialProvider.resolve(any(IntegrationRef.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendForWorkspace(7L, "C1ABCDEFGH", List.of(), "fallback"))
            .isInstanceOf(SlackSendException.class)
            .satisfies(ex -> {
                SlackSendException sse = (SlackSendException) ex;
                assertThat(sse.workspaceId()).isEqualTo(7L);
                assertThat(sse.channelId()).isEqualTo("C1ABCDEFGH");
                assertThat(sse.slackError()).isEqualTo("no_active_slack_connection");
            });
    }
}
