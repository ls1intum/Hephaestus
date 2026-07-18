package de.tum.cit.aet.hephaestus.integration.scm.github.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ProvisioningListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.NatsMessageDeserializer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.project.ProjectIntegrityService;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.github.repository.dto.GitHubRepositoryRefDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the real-time half of the rename/transfer fix: a {@code repository.renamed} /
 * {@code repository.transferred} webhook must re-key BOTH the mirrored domain row and every
 * workspace's {@code RepositoryToMonitor} — by the provider-stable {@code repository.id}, never by the
 * name that just moved — and rebuild the NATS consumer filters. Skipping the monitor half leaves the
 * repo-scoped subject filter pinned to the old name, so every later issue/PR/review/push event for the
 * repository is silently ACK-dropped until the next reconcile cycle (the pre-fix freeze).
 */
class GitHubRepositoryMessageHandlerRenameTest extends BaseUnitTest {

    private static final long NATIVE_ID = 1087937297L;
    private static final long PROVIDER_ID = 7L;
    private static final String OLD_FULL_NAME = "HephaestusTest/payload-fixture-repo";
    private static final String NEW_FULL_NAME = "HephaestusTest/payload-fixture-repo-renamed";
    private static final String NEW_NAME = "payload-fixture-repo-renamed";

    @Mock
    private ProvisioningListener provisioningListener;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ProjectIntegrityService projectIntegrityService;

    @Mock
    private IdentityProviderRepository gitProviderRepository;

    @Mock
    private SyncTargetProvider syncTargetProvider;

    @Mock
    private NatsMessageDeserializer deserializer;

    @Mock
    private IdentityProvider gitHubProvider;

    private GitHubRepositoryMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GitHubRepositoryMessageHandler(
            provisioningListener,
            repositoryRepository,
            projectIntegrityService,
            gitProviderRepository,
            syncTargetProvider,
            deserializer,
            new TransactionTemplate()
        );
    }

    private void stubProvider() {
        when(gitHubProvider.getId()).thenReturn(PROVIDER_ID);
        when(
            gitProviderRepository.findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
        ).thenReturn(Optional.of(gitHubProvider));
    }

    private static GitHubRepositoryRefDTO repositoryRef(String name, String fullName) {
        return new GitHubRepositoryRefDTO(NATIVE_ID, "R_node", name, fullName, false, null, "main");
    }

    private static GitHubRepositoryEventDTO renamedEvent() {
        var changes = new GitHubRepositoryEventDTO.Changes(
            new GitHubRepositoryEventDTO.Changes.RepositoryChanges(
                new GitHubRepositoryEventDTO.Changes.NameChange("payload-fixture-repo")
            ),
            null
        );
        return new GitHubRepositoryEventDTO("renamed", repositoryRef(NEW_NAME, NEW_FULL_NAME), changes, null, null);
    }

    private static GitHubRepositoryEventDTO transferredEvent() {
        var changes = new GitHubRepositoryEventDTO.Changes(
            null,
            new GitHubRepositoryEventDTO.Changes.OwnerChanges(Map.of("user", Map.of("login", "FelixTJDietrich")))
        );
        return new GitHubRepositoryEventDTO(
            "transferred",
            repositoryRef("TestRepository", "HephaestusTest/TestRepository"),
            changes,
            null,
            null
        );
    }

    @Test
    void renameUpdatesDomainRowAndHealsEveryMonitorByNativeId() {
        stubProvider();
        Repository repository = new Repository();
        repository.setName("payload-fixture-repo");
        repository.setNameWithOwner(OLD_FULL_NAME);
        when(repositoryRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
            Optional.of(repository)
        );

        handler.handleEvent(renamedEvent());

        assertThat(repository.getName()).isEqualTo(NEW_NAME);
        assertThat(repository.getNameWithOwner()).isEqualTo(NEW_FULL_NAME);
        assertThat(repository.getHtmlUrl()).isEqualTo("https://github.com/" + NEW_FULL_NAME);
        verify(repositoryRepository).save(repository);
        // The monitor re-key + consumer rebuild — without this the live feed stays frozen.
        verify(syncTargetProvider).reconcileSyncTargetsForRepository(NATIVE_ID, NEW_FULL_NAME);
        // Never by the stale name.
        verify(repositoryRepository, never()).findByNameWithOwner(anyString());
    }

    @Test
    void transferIntoTheOrgIsHealedLikeARename() {
        stubProvider();
        Repository repository = new Repository();
        repository.setNameWithOwner("FelixTJDietrich/TestRepository");
        when(repositoryRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
            Optional.of(repository)
        );

        handler.handleEvent(transferredEvent());

        assertThat(repository.getNameWithOwner()).isEqualTo("HephaestusTest/TestRepository");
        verify(syncTargetProvider).reconcileSyncTargetsForRepository(NATIVE_ID, "HephaestusTest/TestRepository");
    }

    @Test
    void monitorIsHealedEvenWhenNoDomainRowIsMirroredYet() {
        stubProvider();
        when(repositoryRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(Optional.empty());
        when(repositoryRepository.findByNameWithOwner(OLD_FULL_NAME)).thenReturn(Optional.empty());

        handler.handleEvent(renamedEvent());

        verify(repositoryRepository, never()).save(any());
        // The monitor is what the NATS filter is built from, so it must be re-keyed regardless.
        verify(syncTargetProvider).reconcileSyncTargetsForRepository(NATIVE_ID, NEW_FULL_NAME);
    }

    @Test
    void fallsBackToThePreviousNameForLegacyRowsWithoutANativeId() {
        Repository repository = new Repository();
        repository.setNameWithOwner(OLD_FULL_NAME);
        // No IdentityProvider row configured -> the stable-id lookup cannot run.
        when(
            gitProviderRepository.findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
        ).thenReturn(Optional.empty());
        when(repositoryRepository.findByNameWithOwner(OLD_FULL_NAME)).thenReturn(Optional.of(repository));

        handler.handleEvent(renamedEvent());

        assertThat(repository.getNameWithOwner()).isEqualTo(NEW_FULL_NAME);
        verify(syncTargetProvider).reconcileSyncTargetsForRepository(NATIVE_ID, NEW_FULL_NAME);
    }

    @Test
    void unrelatedActionsDoNotTouchTheMonitors() {
        var archived = new GitHubRepositoryEventDTO(
            "archived",
            repositoryRef(NEW_NAME, NEW_FULL_NAME),
            null,
            null,
            null
        );
        when(repositoryRepository.findByNameWithOwner(NEW_FULL_NAME)).thenReturn(Optional.empty());

        handler.handleEvent(archived);

        verifyNoInteractions(syncTargetProvider);
        verify(provisioningListener, never()).onRepositoriesRemoved(anyLong(), any());
    }
}
