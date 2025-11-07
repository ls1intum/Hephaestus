package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubLabelSyncIntegrationTest extends AbstractGitHubSyncIntegrationTest {

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubLabelSyncService labelSyncService;

    @Autowired
    private LabelRepository labelRepository;

    @Test
    void syncsLabelsAndUpdatesMetadata() throws Exception {
        var repository = createEphemeralRepository("label-sync");
        var createdLabel = createRepositoryLabel(repository, "it-label", "00ffaa", "Focused label sync coverage");

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();

        labelSyncService.syncLabelsOfRepository(repository);

        var storedLabel = labelRepository
            .findByRepositoryIdAndName(repository.getId(), createdLabel.getName())
            .orElseThrow();
        assertThat(storedLabel.getColor()).isEqualTo("00ffaa");
        assertThat(storedLabel.getDescription()).isEqualTo("Focused label sync coverage");

        createdLabel.update().color("ff0077").description("Updated label sync coverage").done();

        labelSyncService.syncLabelsOfRepository(repository);

        var updatedLabel = labelRepository
            .findByRepositoryIdAndName(repository.getId(), createdLabel.getName())
            .orElseThrow();
        assertThat(updatedLabel.getColor()).isEqualTo("ff0077");
        assertThat(updatedLabel.getDescription()).isEqualTo("Updated label sync coverage");

        createdLabel.delete();
        awaitCondition("label removed remotely", () -> {
            try {
                return repository
                    .listLabels()
                    .withPageSize(30)
                    .toList()
                    .stream()
                    .noneMatch(label -> label.getId() == createdLabel.getId());
            } catch (IOException listingError) {
                return false;
            }
        });

        labelSyncService.syncLabelsOfRepository(repository);

        assertThat(labelRepository.findByRepositoryIdAndName(repository.getId(), createdLabel.getName())).isEmpty();
    }
}
