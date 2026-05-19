package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for {@link ActivityEventRepository#backfillCommitActors(Long, double)}.
 *
 * <p>Gap #6 reconciliation: commits ingested before GitLab authors are resolved
 * land COMMIT_CREATED activity events with {@code actor_id = NULL, xp = 0}. Once
 * {@code git_commit.author_id} is backfilled via email match, this native UPDATE
 * rewrites the activity-event columns so the contributor actually receives XP.
 */
@DisplayName("ActivityEventRepository.backfillCommitActors Integration")
class ActivityEventRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private CommitRepository commitRepository;

    private Workspace workspace;
    private GitProvider gitProvider;
    private User author;
    private Repository targetRepository;
    private Repository otherRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = new Workspace();
        workspace.setWorkspaceSlug("backfill-workspace");
        workspace.setDisplayName("Backfill Workspace");
        workspace.setAccountLogin("backfill-org");
        workspace.setAccountType(AccountType.ORG);
        workspace = workspaceRepository.save(workspace);

        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.com")));

        author = new User();
        author.setNativeId(900L);
        author.setLogin("alice");
        author.setName("Alice");
        author.setAvatarUrl("https://example.com/alice.png");
        author.setHtmlUrl("https://gitlab.com/alice");
        author.setType(User.Type.USER);
        author.setCreatedAt(Instant.now());
        author.setUpdatedAt(Instant.now());
        author.setProvider(gitProvider);
        author = userRepository.save(author);

        targetRepository = persistRepository(5001L, "backfill-org/target");
        otherRepository = persistRepository(5002L, "backfill-org/sibling");
    }

    private Repository persistRepository(long nativeId, String nameWithOwner) {
        Repository repo = new Repository();
        repo.setNativeId(nativeId);
        repo.setName(nameWithOwner.substring(nameWithOwner.indexOf('/') + 1));
        repo.setNameWithOwner(nameWithOwner);
        repo.setHtmlUrl("https://gitlab.com/" + nameWithOwner);
        repo.setVisibility(Repository.Visibility.PRIVATE);
        repo.setDefaultBranch("main");
        repo.setPushedAt(Instant.now());
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setProvider(gitProvider);
        return repositoryRepository.save(repo);
    }

    private Commit persistCommit(String sha, Repository repo, User commitAuthor) {
        Commit commit = new Commit();
        commit.setSha(sha);
        commit.setMessage("initial commit");
        commit.setAuthoredAt(Instant.parse("2024-06-01T10:00:00Z"));
        commit.setCommittedAt(Instant.parse("2024-06-01T10:00:00Z"));
        commit.setRepository(repo);
        commit.setAuthor(commitAuthor);
        commit.setAuthorEmail("alice@example.com");
        return commitRepository.save(commit);
    }

    private ActivityEvent persistCommitCreatedEvent(Commit commit, User actor, double xp) {
        Instant occurredAt = commit.getAuthoredAt();
        ActivityEvent event = ActivityEvent.builder()
            .id(UUID.randomUUID())
            .eventKey(
                ActivityEventType.COMMIT_CREATED.getValue() +
                    ":" +
                    commit.getId() +
                    ":" +
                    occurredAt.toEpochMilli() +
                    ":" +
                    UUID.randomUUID()
            )
            .eventType(ActivityEventType.COMMIT_CREATED)
            .occurredAt(occurredAt)
            .actor(actor)
            .workspace(workspace)
            .repository(commit.getRepository())
            .targetType(ActivityTargetType.COMMIT.getValue())
            .targetId(commit.getId())
            .xp(xp)
            .ingestedAt(Instant.now())
            .build();
        return activityEventRepository.save(event);
    }

    @Test
    @DisplayName("rewrites actor_id and xp for orphan COMMIT_CREATED events once git_commit.author_id is set")
    void backfillsOrphanCommitEvents() {
        Commit commit = persistCommit("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", targetRepository, null);
        ActivityEvent persisted = persistCommitCreatedEvent(commit, null, 0.0);

        // Simulate the post-enrichment state: author_id resolved on git_commit.
        commit.setAuthor(author);
        commitRepository.save(commit);

        int updated = activityEventRepository.backfillCommitActors(targetRepository.getId(), 5.0);

        assertThat(updated).isEqualTo(1);
        ActivityEvent refreshed = activityEventRepository.findById(persisted.getId()).orElseThrow();
        assertThat(refreshed.getActor()).isNotNull();
        assertThat(refreshed.getActor().getId()).isEqualTo(author.getId());
        assertThat(refreshed.getXp()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("does not touch events whose git_commit still has a null author_id")
    void skipsCommitsWithUnresolvedAuthor() {
        Commit commit = persistCommit("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", targetRepository, null);
        persistCommitCreatedEvent(commit, null, 0.0);

        int updated = activityEventRepository.backfillCommitActors(targetRepository.getId(), 5.0);

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("does not touch events whose actor was already resolved at ingest time")
    void skipsEventsAlreadyHavingActor() {
        Commit commit = persistCommit("cccccccccccccccccccccccccccccccccccccccc", targetRepository, author);
        ActivityEvent persisted = persistCommitCreatedEvent(commit, author, 5.0);

        int updated = activityEventRepository.backfillCommitActors(targetRepository.getId(), 7.0);

        assertThat(updated).isZero();
        ActivityEvent refreshed = activityEventRepository.findById(persisted.getId()).orElseThrow();
        assertThat(refreshed.getXp()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("scopes backfill to the requested repository id")
    void scopesBackfillToRequestedRepository() {
        Commit targetCommit = persistCommit("dddddddddddddddddddddddddddddddddddddddd", targetRepository, null);
        Commit otherCommit = persistCommit("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", otherRepository, null);
        ActivityEvent targetEvent = persistCommitCreatedEvent(targetCommit, null, 0.0);
        ActivityEvent otherEvent = persistCommitCreatedEvent(otherCommit, null, 0.0);

        targetCommit.setAuthor(author);
        otherCommit.setAuthor(author);
        commitRepository.save(targetCommit);
        commitRepository.save(otherCommit);

        int updated = activityEventRepository.backfillCommitActors(targetRepository.getId(), 5.0);

        assertThat(updated).isEqualTo(1);
        ActivityEvent refreshedTarget = activityEventRepository.findById(targetEvent.getId()).orElseThrow();
        ActivityEvent refreshedOther = activityEventRepository.findById(otherEvent.getId()).orElseThrow();
        assertThat(refreshedTarget.getActor()).isNotNull();
        assertThat(refreshedTarget.getActor().getId()).isEqualTo(author.getId());
        assertThat(refreshedTarget.getXp()).isEqualTo(5.0);
        assertThat(refreshedOther.getActor()).isNull();
        assertThat(refreshedOther.getXp()).isZero();
    }

    @Test
    @DisplayName("ignores non-commit activity events for the same repository")
    void ignoresNonCommitEvents() {
        Instant occurredAt = Instant.parse("2024-06-01T10:00:00Z");
        ActivityEvent prEvent = ActivityEvent.builder()
            .id(UUID.randomUUID())
            .eventKey(ActivityEventType.PULL_REQUEST_OPENED.getValue() + ":42:" + occurredAt.toEpochMilli())
            .eventType(ActivityEventType.PULL_REQUEST_OPENED)
            .occurredAt(occurredAt)
            .actor(null)
            .workspace(workspace)
            .repository(targetRepository)
            .targetType(ActivityTargetType.PULL_REQUEST.getValue())
            .targetId(42L)
            .xp(0.0)
            .ingestedAt(Instant.now())
            .build();
        activityEventRepository.save(prEvent);

        int updated = activityEventRepository.backfillCommitActors(targetRepository.getId(), 5.0);

        assertThat(updated).isZero();
        ActivityEvent refreshed = activityEventRepository.findById(prEvent.getId()).orElseThrow();
        assertThat(refreshed.getActor()).isNull();
        assertThat(refreshed.getXp()).isZero();
    }
}
