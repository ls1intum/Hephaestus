package de.tum.cit.aet.hephaestus.integration.outline.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.CredentialBundleConverter;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.OAuthSession;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@DisplayName("OutlineCredentialProvider — unit")
class OutlineCredentialProviderTest extends BaseUnitTest {

    @Mock private ConnectionService connectionService;

    private CredentialBundleConverter converter;
    private OutlineCredentialProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        converter = new CredentialBundleConverter("0123456789abcdef0123456789abcdef", "dev");
        provider = new OutlineCredentialProvider(connectionService, converter);
    }

    @Test
    @DisplayName("ACTIVE Outline Connection with encrypted OAuth session decrypts back faithfully")
    void activeConnection_decryptsOAuthSession() {
        long workspaceId = 17L;
        Workspace ws = Mockito.mock(Workspace.class);
        Connection connection = new Connection(
            ws, IntegrationKind.OUTLINE, "ws-ext-1",
            new ConnectionConfig.OutlineConfig("https://app.getoutline.com", "ws-ext-1", Set.of())
        );
        OAuthSession session = new OAuthSession(
            "access-abc", "refresh-xyz", Instant.parse("2030-01-01T00:00:00Z"));
        connection.setCredentials(session, converter);
        connection.setState(IntegrationState.ACTIVE);
        when(connectionService.findActive(workspaceId, IntegrationKind.OUTLINE))
            .thenReturn(Optional.of(connection));

        Optional<CredentialBundle> resolved = provider.resolve(
            new IntegrationRef(IntegrationKind.OUTLINE, workspaceId, "ws-ext-1"));

        assertThat(resolved).contains(session);
    }
}
