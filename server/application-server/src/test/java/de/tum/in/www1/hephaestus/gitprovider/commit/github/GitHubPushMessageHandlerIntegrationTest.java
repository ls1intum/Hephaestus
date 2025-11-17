package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

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
        assertThat(commit.getMessage()).isEqualTo(pushCommit.getMessage());
        assertThat(commit.getCompareUrl()).isEqualTo(payload.getCompare());
        assertThat(commit.getBeforeSha()).isEqualTo(payload.getBefore());
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
        assertThat(reloaded.getLastSyncAt()).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void shouldCaptureMetadataForNewBranchCommits(
        @GitHubPayload("push.branch-rename-binary") GHEventPayload.Push payload
    ) throws Exception {
        handler.handleEvent(payload);

        var pushCommit = payload.getCommits().get(payload.getCommits().size() - 1);
        var commit = commitRepository
            .findById(pushCommit.getSha())
            .orElseThrow(() -> new AssertionError("Commit not persisted"));

        assertThat(commit.getCommitUrl()).isEqualTo(pushCommit.getUrl());
        assertThat(commit.getCompareUrl()).isEqualTo(payload.getCompare());
        assertThat(commit.getFileChanges()).extracting(GitCommitFileChange::getPath).contains("assets/snapshot.bin");
    }

    @Test
    void shouldMarkForcePushCommits(@GitHubPayload("push.force-branch-rewrite") GHEventPayload.Push payload)
        throws Exception {
        handler.handleEvent(payload);

        var pushCommit = payload.getCommits().get(0);
        var commit = commitRepository
            .findById(pushCommit.getSha())
            .orElseThrow(() -> new AssertionError("Commit not persisted"));

        assertThat(commit.getBeforeSha()).isEqualTo(payload.getBefore());
    }

    @Test
    void shouldLinkExistingAuthorAndCommitter(@GitHubPayload("push") GHEventPayload.Push payload) throws Exception {
        var login = payload.getCommits().get(0).getAuthor().getUsername();
        userRepository.save(sampleUser(5898705L, login));

        handler.handleEvent(payload);

        var commit = commitRepository
            .findById(payload.getCommits().get(0).getSha())
            .orElseThrow(() -> new AssertionError("Commit not persisted"));

        assertThat(commit.getAuthor()).isNotNull();
        assertThat(commit.getAuthor().getLogin()).isEqualTo(login);
        assertThat(commit.getCommitter()).isNotNull();
        assertThat(commit.getCommitter().getLogin()).isEqualTo(login);
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

    private User sampleUser(long id, String login) {
        var user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setName(login);
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setNotificationsEnabled(true);
        user.setParticipateInResearch(true);
        return user;
    }
}
