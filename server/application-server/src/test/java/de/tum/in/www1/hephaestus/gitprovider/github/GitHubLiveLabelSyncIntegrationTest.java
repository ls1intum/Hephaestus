package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubLiveLabelSyncIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    private static final String LABEL_PREFIX = "it-label";
    private static final String INITIAL_COLOR = "00ffaa";
    private static final String UPDATED_COLOR = "ff0077";
    private static final String LABEL_DESCRIPTION = "Focused label sync coverage";

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubLabelSyncService labelSyncService;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Test
    void syncsNewLabels() throws Exception {
        var seeded = seedRepositoryWithLabel("label-sync");
        var repository = seeded.repository();
        var createdLabel = seeded.label();

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();
        var localRepo = repositoryRepository.findByNameWithOwner(repository.getFullName()).orElseThrow();
        labelSyncService.syncLabelsForRepository(workspace.getId(), localRepo.getId());

        var storedLabel = labelRepository
            .findByRepositoryIdAndName(localRepo.getId(), createdLabel.getName())
            .orElseThrow();
        assertThat(storedLabel.getColor()).isEqualTo(INITIAL_COLOR);
    }

    @Test
    void syncsLabelUpdates() throws Exception {
        var seeded = seedRepositoryWithLabel("label-update-sync");
        var repository = seeded.repository();
        var createdLabel = seeded.label();

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();
        var localRepo = repositoryRepository.findByNameWithOwner(repository.getFullName()).orElseThrow();
        labelSyncService.syncLabelsForRepository(workspace.getId(), localRepo.getId());

        createdLabel.update().color(UPDATED_COLOR).description("Updated label sync coverage").done();
        labelSyncService.syncLabelsForRepository(workspace.getId(), localRepo.getId());

        var updatedLabel = labelRepository
            .findByRepositoryIdAndName(localRepo.getId(), createdLabel.getName())
            .orElseThrow();
        assertThat(updatedLabel.getColor()).isEqualTo(UPDATED_COLOR);
    }

    @Test
    void removesDeletedLabels() throws Exception {
        var seeded = seedRepositoryWithLabel("label-delete-sync");
        var repository = seeded.repository();
        var createdLabel = seeded.label();

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();
        var localRepo = repositoryRepository.findByNameWithOwner(repository.getFullName()).orElseThrow();
        labelSyncService.syncLabelsForRepository(workspace.getId(), localRepo.getId());

        createdLabel.delete();
        awaitCondition("label removed remotely", () -> isLabelMissingRemotely(repository, createdLabel));

        labelSyncService.syncLabelsForRepository(workspace.getId(), localRepo.getId());

        assertThat(labelRepository.findByRepositoryIdAndName(localRepo.getId(), createdLabel.getName())).isEmpty();
    }

    private RepositoryLabel seedRepositoryWithLabel(String suffix) throws Exception {
        var repository = createEphemeralRepository(suffix);
        var label = createRepositoryLabel(repository, LABEL_PREFIX, INITIAL_COLOR, LABEL_DESCRIPTION);
        return new RepositoryLabel(repository, label);
    }

    private boolean isLabelMissingRemotely(GHRepository repository, GHLabel label) {
        try {
            return repository
                .listLabels()
                .withPageSize(30)
                .toList()
                .stream()
                .noneMatch(remote -> remote.getId() == label.getId());
        } catch (IOException listingError) {
            return false;
        }
    }

    private record RepositoryLabel(GHRepository repository, GHLabel label) {}
}
