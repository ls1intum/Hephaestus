package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.slack.credentials.SlackCredentialProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link SlackMessageService}'s credential resolution + early-skip
 * branches. The SDK call paths (chat.postMessage / users.list / auth.test) are covered
 * indirectly — they execute against {@code Slack.getInstance()}, which is not
 * conveniently mockable, so the live HTTP behaviour is covered in Phase B via the
 * mocked OAuth flow + connect+post integration test.
 */
class SlackMessageServiceTest extends BaseUnitTest {

    @Mock
    private SlackCredentialProvider credentialProvider;

    private SlackMessageService service;

    @BeforeEach
    void setUp() {
        service = new SlackMessageService(credentialProvider);
    }

    @Test
    void initTest_returnsFalseWhenNoToken() {
        when(credentialProvider.resolve(any(IntegrationRef.class))).thenReturn(Optional.empty());

        boolean result = service.initTest(7L);

        assertThat(result).isFalse();
    }

    @Test
    void initTest_returnsFalseWhenWrongBundleVariant() {
        when(credentialProvider.resolve(any(IntegrationRef.class))).thenReturn(
            Optional.of(
                (CredentialBundle) new de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.OAuthSession(
                    "a",
                    "b",
                    null
                )
            )
        );

        boolean result = service.initTest(7L);

        assertThat(result).isFalse();
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

    @Test
    void listMembers_noToken_returnsEmptyList() {
        when(credentialProvider.resolve(any(IntegrationRef.class))).thenReturn(Optional.empty());

        List<com.slack.api.model.User> members = service.listMembers(7L);

        assertThat(members).isEmpty();
    }

    @Test
    void slackSendException_carriesStructuredFields() {
        SlackSendException ex = new SlackSendException(99L, "C0974LJBPBK", "channel_not_found");
        assertThat(ex.workspaceId()).isEqualTo(99L);
        assertThat(ex.channelId()).isEqualTo("C0974LJBPBK");
        assertThat(ex.slackError()).isEqualTo("channel_not_found");
        assertThat(ex.getMessage()).contains("channel_not_found").contains("C0974LJBPBK").contains("99");
    }
}
