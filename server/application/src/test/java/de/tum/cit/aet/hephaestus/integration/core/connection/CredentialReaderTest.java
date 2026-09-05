package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.security.MissingCredentialKeyException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@Tag("unit")
class CredentialReaderTest extends BaseUnitTest {

    private static final String KEY_A = "0123456789abcdef0123456789abcdef";
    private static final String KEY_B = "ABCDEFabcdef0123ABCDEFabcdef0123";
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");
    private static final BearerToken TOKEN = new BearerToken("xoxb-stored-under-key-a", null);

    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private Connection connection;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Workspace workspace = new Workspace();
        workspace.setId(7L);
        connection = new Connection(
                workspace,
                IntegrationKind.SLACK,
                "T0974LHQU7K",
                new ConnectionConfig.SlackConfig("T0974LHQU7K", "hephaestus-test", null, null, null, Set.of()));
        ReflectionTestUtils.setField(connection, "id", 55L);
    }

    private CredentialReader readerWith(CredentialBundleConverter converter) {
        return new CredentialReader(
                connectionRepository, converter, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldReturnTheCredentialWhenTheKeyReadsIt() {
        CredentialBundleConverter converter = new CredentialBundleConverter(KEY_A, "dev");
        connection.setCredentials(TOKEN, converter);

        assertThat(readerWith(converter).credentialsOf(connection)).contains(TOKEN);
        verify(connectionRepository, never()).markCredentialsReadable(any(), any(), any());
    }

    @Test
    void shouldMarkTheConnectionAndNameItWhenTheKeyCannotReadTheCredential() {
        connection.setCredentials(TOKEN, new CredentialBundleConverter(KEY_A, "dev"));

        assertThatThrownBy(() ->
                        readerWith(new CredentialBundleConverter(KEY_B, "dev")).credentialsOf(connection))
                .isInstanceOf(CredentialUnreadableException.class)
                .satisfies(e -> {
                    CredentialUnreadableException unreadable = (CredentialUnreadableException) e;
                    assertThat(unreadable.connectionId()).isEqualTo(55L);
                    assertThat(unreadable.kind()).isEqualTo(IntegrationKind.SLACK);
                })
                .hasMessageContaining("connection 55")
                .hasMessageContaining("Replace the credential");
        // Outside a transaction the record is written at once, bound to the ciphertext that failed.
        verify(connectionRepository)
                .markCredentialsUnreadable(eq(55L), eq(7L), eq(connection.getCredentialsEncrypted()), eq(NOW));
    }

    @Test
    void shouldClearTheRecordWhenAMarkedCredentialReadsAgain() {
        CredentialBundleConverter converter = new CredentialBundleConverter(KEY_A, "dev");
        connection.setCredentials(TOKEN, converter);
        connection.markCredentialRotationFailed(NOW.minusSeconds(60));

        assertThat(readerWith(converter).credentialsOf(connection)).contains(TOKEN);
        verify(connectionRepository).markCredentialsReadable(eq(55L), eq(7L), eq(connection.getCredentialsEncrypted()));
    }

    @Test
    void shouldLetAMissingKeyVersionThroughWithoutMarking() {
        // Written under key version 2; a server that only holds version 1 has an instance fault,
        // not a credential to give up on.
        connection.setCredentials(TOKEN, new CredentialBundleConverter(KEY_A, 2, null, null, "dev"));

        assertThatThrownBy(() ->
                        readerWith(new CredentialBundleConverter(KEY_A, "dev")).credentialsOf(connection))
                .isInstanceOf(MissingCredentialKeyException.class);
        verify(connectionRepository, never()).markCredentialsUnreadable(any(), any(), any(), any());
    }
}
