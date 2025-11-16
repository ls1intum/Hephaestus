package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Push Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubPushMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPushMessageHandler handler;

    @Autowired
    private GitCommitRepository commitRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    void shouldReturnCorrectEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.PUSH);
    }

    @Test
    void shouldPersistCommitOnPush(@GitHubPayload("push") GHEventPayload.Push payload) throws Exception {
        handler.handleEvent(payload);

        var pushCommit = payload.getCommits().get(0);
        GitCommit commit = commitRepository
            .findById(pushCommit.getSha())
            .orElseThrow(() -> new AssertionError("Commit not persisted"));

        assertThat(commit.getRepository()).isNotNull();
        assertThat(commit.getRefName()).isEqualTo(payload.getRef());
        assertThat(commit.isHeadCommit()).isTrue();
        assertThat(commit.getMessage()).isEqualTo(pushCommit.getMessage());
        var expectedPaths = allPaths(pushCommit);
        assertThat(commit.getFileChanges()).hasSize(expectedPaths.size());
        assertThat(commit.getAuthorName()).isEqualTo(pushCommit.getAuthor().getName());
    }

    @Test
    void shouldReplaceFileChangesOnReplay(@GitHubPayload("push") GHEventPayload.Push payload) throws Exception {
        handler.handleEvent(payload);
        var pushCommit = payload.getCommits().get(0);
        var commit = commitRepository
            .findById(pushCommit.getSha())
            .orElseThrow(() -> new AssertionError("Commit not persisted"));

        var extra = new GitCommitFileChange();
        extra.setChangeType(GitCommitFileChange.ChangeType.MODIFIED);
        extra.setPath("unexpected.txt");
        extra.setCommit(commit);
        commit.getFileChanges().add(extra);
        commitRepository.save(commit);

        handler.handleEvent(payload);

        var reloaded = commitRepository
            .findById(pushCommit.getSha())
            .orElseThrow(() -> new AssertionError("Commit disappeared"));
        var expectedPaths = allPaths(pushCommit);
        assertThat(reloaded.getFileChanges())
            .extracting(GitCommitFileChange::getPath)
            .containsExactlyInAnyOrderElementsOf(expectedPaths);
        assertThat(reloaded.getLastSyncedAt()).isAfter(Instant.now().minusSeconds(60));
    }

    private java.util.List<String> allPaths(GHEventPayload.Push.PushCommit commit) {
        var paths = new java.util.ArrayList<String>();
        if (commit.getAdded() != null) {
            paths.addAll(commit.getAdded());
        }
        if (commit.getRemoved() != null) {
            paths.addAll(commit.getRemoved());
        }
        if (commit.getModified() != null) {
            paths.addAll(commit.getModified());
        }
        return paths;
    }
}
