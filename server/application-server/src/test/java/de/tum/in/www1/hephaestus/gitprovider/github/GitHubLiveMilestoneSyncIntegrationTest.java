package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssueState;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubLiveMilestoneSyncIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubMilestoneSyncService milestoneSyncService;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Test
    void syncsMilestonesAndReflectsUpdates() throws Exception {
        var repository = createEphemeralRepository("milestone-sync");
        var milestone = createRepositoryMilestone(repository, "IT milestone", "Focused milestone sync coverage");

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();

        milestoneSyncService.syncMilestonesOfRepository(repository);

        var storedMilestone = milestoneRepository
            .findAll()
            .stream()
            .filter(candidate -> candidate.getRepository().getId().equals(repository.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(storedMilestone.getTitle()).isEqualTo(milestone.getTitle());
        assertThat(storedMilestone.getState()).isEqualTo(Milestone.State.OPEN);

        milestone.close();

        milestoneSyncService.syncMilestonesOfRepository(repository);

        var updatedMilestone = milestoneRepository
            .findAll()
            .stream()
            .filter(candidate -> candidate.getRepository().getId().equals(repository.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(updatedMilestone.getState()).isEqualTo(Milestone.State.CLOSED);

        milestone.delete();
        awaitCondition("milestone removed remotely", () -> {
            try {
                return repository
                    .listMilestones(GHIssueState.ALL)
                    .withPageSize(30)
                    .toList()
                    .stream()
                    .noneMatch(candidate -> candidate.getId() == milestone.getId());
            } catch (IOException listingError) {
                return false;
            }
        });

        milestoneSyncService.syncMilestonesOfRepository(repository);

        assertThat(milestoneRepository.findAllByRepository_Id(repository.getId())).isEmpty();
    }
}
