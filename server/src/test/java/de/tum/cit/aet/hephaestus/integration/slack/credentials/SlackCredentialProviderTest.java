package de.tum.cit.aet.hephaestus.integration.slack.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@DisplayName("SlackCredentialProvider — unit")
class SlackCredentialProviderTest extends BaseUnitTest {

    @Mock private ConnectionService connectionService;

    private CredentialBundleConverter converter;
    private SlackCredentialProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new CredentialBundleConverter("0123456789abcdef0123456789abcdef", "dev");
        provider = new SlackCredentialProvider(connectionService, converter);
    }

    @Test
    @DisplayName("ACTIVE Slack Connection with encrypted xoxb token decrypts back to BearerToken")
    void activeConnection_decryptsBotToken() {
        long workspaceId = 17L;
        Workspace ws = Mockito.mock(Workspace.class);
        Connection connection = new Connection(
            ws, IntegrationKind.SLACK, "T12345",
            new ConnectionConfig.SlackConfig("T12345", "Acme", null, null, Set.of())
        );
        connection.setCredentials(new BearerToken("xoxb-secret-bot-token", null), converter);
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.SLACK))
            .thenReturn(Optional.of(connection));

        Optional<CredentialBundle> resolved = provider.resolve(
            new IntegrationRef(IntegrationKind.SLACK, workspaceId, "T12345"));

        assertThat(resolved).contains(new BearerToken("xoxb-secret-bot-token", null));
    }
}
