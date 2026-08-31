package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.core.security.SecurityProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Batch contract for {@link CredentialRotationService#rotateBatch()}: a locked batch is
 * re-encrypted under the active key and stamped with the active version, legacy rows with a
 * {@code NULL} key version decrypt through the prior key, an empty batch is a no-op, and a row
 * whose version matches no configured key fails closed instead of being rewritten.
 */
@Tag("unit")
class CredentialRotationServiceTest extends BaseUnitTest {

    private static final String OLD_KEY = "0123456789abcdef0123456789abcdef";
    private static final String NEW_KEY = "ABCDEFabcdef0123ABCDEFabcdef0123";
    private static final BearerToken TOKEN = new BearerToken("glpat-rotate-me", null);

    @Mock
    private ConnectionRepository connectionRepository;

    private CredentialBundleConverter oldConverter;
    private CredentialBundleConverter rotatingConverter;
    private CredentialRotationService service;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        oldConverter = new CredentialBundleConverter(OLD_KEY, "dev");
        rotatingConverter = new CredentialBundleConverter(NEW_KEY, 2, OLD_KEY, 1, "dev");
        SecurityProperties properties = new SecurityProperties(null, NEW_KEY, 2, OLD_KEY, 1, true, 25);
        service = new CredentialRotationService(connectionRepository, rotatingConverter, properties);
        workspace = new Workspace();
        workspace.setId(7L);
    }

    @Test
    void shouldReEncryptUnderTheActiveKeyAndStampItsVersionWhenABatchIsLocked() {
        Connection stale = connection(55L);
        stale.setCredentials(TOKEN, oldConverter);
        byte[] staleBlob = stale.getCredentialsEncrypted();
        when(connectionRepository.lockCredentialRotationBatch(2, 25)).thenReturn(List.of(55L));
        when(connectionRepository.findAllById(List.of(55L))).thenReturn(List.of(stale));

        service.rotateBatch();

        assertThat(stale.getCredentialsKeyVersion()).isEqualTo(2);
        assertThat(stale.getCredentialsEncrypted()).isNotEqualTo(staleBlob);
        assertThat(stale.credentials(rotatingConverter)).contains(TOKEN);
        // The rewritten blob must not need the prior key any more.
        CredentialBundleConverter activeOnly = new CredentialBundleConverter(NEW_KEY, 2, null, null, "dev");
        assertThat(stale.credentials(activeOnly)).contains(TOKEN);
    }

    @Test
    void shouldDecryptThroughThePriorKeyWhenARowPredatesKeyVersionStamping() {
        Connection legacy = connection(56L);
        legacy.setCredentials(TOKEN, oldConverter);
        setKeyVersion(legacy, null); // Rows written before the credentials_key_version column exist as NULL.
        when(connectionRepository.lockCredentialRotationBatch(2, 25)).thenReturn(List.of(56L));
        when(connectionRepository.findAllById(List.of(56L))).thenReturn(List.of(legacy));

        service.rotateBatch();

        assertThat(legacy.getCredentialsKeyVersion()).isEqualTo(2);
        assertThat(legacy.credentials(rotatingConverter)).contains(TOKEN);
    }

    @Test
    void shouldNotLoadAnyRowWhenEveryCredentialAlreadyUsesTheActiveKey() {
        when(connectionRepository.lockCredentialRotationBatch(2, 25)).thenReturn(List.of());

        service.rotateBatch();

        verify(connectionRepository, never()).findAllById(ArgumentMatchers.anyIterable());
    }

    @Test
    void shouldFailClosedWhenARowsKeyVersionMatchesNoConfiguredKey() {
        Connection orphaned = connection(57L);
        orphaned.setCredentials(TOKEN, oldConverter);
        setKeyVersion(orphaned, 99);
        byte[] blobBefore = orphaned.getCredentialsEncrypted();
        when(connectionRepository.lockCredentialRotationBatch(2, 25)).thenReturn(List.of(57L));
        when(connectionRepository.findAllById(List.of(57L))).thenReturn(List.of(orphaned));

        assertThatThrownBy(() -> service.rotateBatch())
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("No encryption key configured");
        assertThat(orphaned.getCredentialsEncrypted()).isEqualTo(blobBefore);
        assertThat(orphaned.getCredentialsKeyVersion()).isEqualTo(99);
    }

    private Connection connection(long id) {
        Connection connection = new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "100",
                new ConnectionConfig.GitHubAppConfig(100L, null, null, Set.of()));
        setField(connection, "id", id);
        return connection;
    }

    private static void setKeyVersion(Connection connection, @Nullable Integer version) {
        setField(connection, "credentialsKeyVersion", version);
    }

    private static void setField(Connection connection, String name, @Nullable Object value) {
        try {
            Field field = Connection.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(connection, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
