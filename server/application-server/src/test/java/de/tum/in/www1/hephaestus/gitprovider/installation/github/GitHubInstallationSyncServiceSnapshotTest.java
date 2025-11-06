package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTargetRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Installation Sync Snapshot")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubInstallationSyncServiceSnapshotTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationSyncService syncService;

    @Autowired
    private InstallationRepository installationRepository;

    @Autowired
    private InstallationTargetRepository installationTargetRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should persist installation snapshot without webhook context")
    void shouldPersistInstallationSnapshot(@GitHubPayload("installation.created") GHEventPayload.Installation payload) {
        assertThat(payload.getInstallation()).isNotNull();

        var persisted = syncService.synchronizeInstallationSnapshot(payload.getInstallation());

        assertThat(persisted).isNotNull();
        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation was not persisted"));
        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.ACTIVE);
        assertThat(installation.getRepositorySelection()).isEqualTo(Installation.RepositorySelection.ALL);
        assertThat(installation.getSubscribedEvents())
            .containsAll(
                payload
                    .getInstallation()
                    .getEvents()
                    .stream()
                    .map(event -> event.name().toLowerCase())
                    .collect(Collectors.toSet())
            );

        var target = installationTargetRepository
            .findById(payload.getInstallation().getAccount().getId())
            .orElseThrow(() -> new AssertionError("Installation target missing"));
        assertThat(target.getLogin()).isEqualTo(payload.getInstallation().getAccount().getLogin());
    }
}
