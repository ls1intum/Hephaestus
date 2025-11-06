package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTargetRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Installation Target Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubInstallationTargetMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationTargetMessageHandler handler;

    @Autowired
    private InstallationRepository installationRepository;

    @Autowired
    private InstallationTargetRepository installationTargetRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should upsert installation target and propagate rename")
    void shouldUpsertInstallationTargetAndRename(
        @GitHubPayload("installation_target") GHEventPayloadInstallationTarget payload
    ) {
        Installation installation = new Installation();
        installation.setId(payload.getInstallationRef().getId());
        installation.setCreatedAt(Instant.now());
        installation.setUpdatedAt(Instant.now());
        installation.setLifecycleState(Installation.LifecycleState.ACTIVE);
        installation.setRepositorySelection(Installation.RepositorySelection.SELECTED);
        installationRepository.save(installation);

        handler.handleEvent(payload);

        InstallationTarget target = installationTargetRepository
            .findById(payload.getAccount().getId())
            .orElseThrow(() -> new AssertionError("Installation target not persisted"));

        assertThat(target.getLogin()).isEqualTo(payload.getAccount().getLogin());
        assertThat(target.getLastRenamedFrom()).isEqualTo(payload.getChanges().getLogin().getFrom());
        assertThat(target.getLastRenamedAt()).isNotNull();
        assertThat(target.getType()).isEqualTo(InstallationTarget.TargetType.ORGANIZATION);
        assertThat(target.getLastSyncedAt()).isNotNull();

        Installation updatedInstallation = installationRepository
            .findById(payload.getInstallationRef().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));
        assertThat(updatedInstallation.getTarget()).isNotNull();
        assertThat(updatedInstallation.getTarget().getId()).isEqualTo(target.getId());
        assertThat(updatedInstallation.getTargetType()).isEqualTo(target.getType());
    }
}
