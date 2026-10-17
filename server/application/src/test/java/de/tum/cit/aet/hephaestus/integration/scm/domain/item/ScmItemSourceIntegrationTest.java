package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemMergeTimeQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.RoleHolder;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.IssueContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.Commit;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.CommitRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class ScmItemSourceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private ScmItemService sources;

    @Autowired
    private ItemIndexService writer;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private ConnectionRepository connections;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private IssueRepository issues;

    @Autowired
    private PullRequestRepository pulls;

    @Autowired
    private PullRequestReviewRepository reviews;

    @Autowired
    private UserRepository users;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ItemEventRepository events;

    @Autowired
    private MonitoredRepositoryQuery monitored;

    @Autowired
    private ScmItemSourceAccess sourceAccess;

    @Autowired
    private ItemMergeTimeQuery mergeTimes;

    @Autowired
    private CommitRepository commits;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldResolveOnlyTheExactMonitoredNativeRepositoryAndAuthorizeWithoutGit(IdentityProviderType type) {
        var fixture = fixture(type);
        long workspaceId = fixture.workspace().getId();
        var root = writer.upsert(new ItemRegistration(
                workspaceId, ScmItemEvents.REPOSITORY, fixture.repository().getId(), null, null));
        var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        workspaceId, fixture.repository().getNameWithOwner())
                .orElseThrow();
        var reference =
                Objects.requireNonNull(monitored.repositories(workspaceId).get(root.itemId()));
        assertThat(reference.repositoryItemId()).isEqualTo(root.itemId());
        assertThat(reference.monitorId()).isEqualTo(monitor.getId());
        assertThat(reference.nameWithOwner()).isEqualTo(fixture.repository().getNameWithOwner());
        var file =
                writer.upsert(new ItemRegistration(workspaceId, ScmItemEvents.FILE, null, root.itemId(), "src/a.ts"));
        assertThat(sourceAccess.permits(file, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isTrue();
        var foreign = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace(
                "foreign-access-" + UUID.randomUUID().toString().substring(0, 12)));
        assertThat(monitored.repositories(foreign.getId())).isEmpty();
        assertThat(sourceAccess.permits(
                        new ItemRef(foreign.getId(), file.itemId(), file.kind(), null, root.itemId(), file.path()),
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        var otherProvider = providers.saveAndFlush(
                new IdentityProvider(type, "https://other-" + UUID.randomUUID() + ".example.test"));
        var other = new Repository();
        other.setProvider(otherProvider);
        other.setNativeId(fixture.repository().getNativeId());
        other.setName("source");
        other.setNameWithOwner(fixture.repository().getNameWithOwner());
        other.setDefaultBranch("main");
        other.setVisibility(Repository.Visibility.PUBLIC);
        other.setCreatedAt(Instant.now());
        other.setUpdatedAt(Instant.now());
        other.setPushedAt(Instant.now());
        other = repositories.saveAndFlush(other);
        var otherItem =
                writer.upsert(new ItemRegistration(workspaceId, ScmItemEvents.REPOSITORY, other.getId(), null, null));
        assertThat(monitored.repositories(workspaceId)).containsOnlyKeys(root.itemId());
        assertThat(sourceAccess.permits(otherItem, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        monitor.setNativeId(fixture.repository().getNativeId() == 13L ? 14L : 13L);
        monitors.saveAndFlush(monitor);
        assertThat(monitored.repositories(workspaceId)).isEmpty();
        assertThat(sourceAccess.permits(file, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        monitor.setNativeId(fixture.repository().getNativeId());
        monitors.saveAndFlush(monitor);
        writer.remove(workspaceId, root.itemId(), Instant.now());
        assertThat(monitored.repositories(workspaceId)).isEmpty();
        assertThat(sourceAccess.permits(file, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isFalse();
        writer.restore(workspaceId, root.itemId());
        assertThat(monitored.repositories(workspaceId)).containsOnlyKeys(root.itemId());
        assertThat(sourceAccess.permits(file, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldKeepKindsParentsAndProviderIdentitiesDistinct(IdentityProviderType type) throws Exception {
        var fixture = fixture(type);
        var issue = new Issue();
        issue.setNativeId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        issue.setProvider(fixture.provider());
        issue.setRepository(fixture.repository());
        issue.setNumber(7);
        issue.setTitle("An issue");
        issue.setBody("Issue body");
        issue.setState(Issue.State.OPEN);
        issue.setAuthor(fixture.author());
        issue.setHtmlUrl(fixture.repository().getHtmlUrl() + "/issues/7");
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue.setCommentsCount(2);
        issue = issues.saveAndFlush(issue);
        jdbc.update(
                """
                INSERT INTO issue_comment(native_id,provider_id,issue_id,body,html_url,author_association,created_at,author_id)
                VALUES (?,?,?,'second','https://example.test/comment/second','MEMBER',?,?),
                       (?,?,?,'first','https://example.test/comment/first','MEMBER',?,?)
                """,
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                fixture.provider().getId(),
                issue.getId(),
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")),
                fixture.author().getId(),
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                fixture.provider().getId(),
                issue.getId(),
                Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")),
                fixture.author().getId());
        var pull = new PullRequest();
        pull.setNativeId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        pull.setProvider(fixture.provider());
        pull.setRepository(fixture.repository());
        pull.setNumber(7);
        pull.setTitle("A pull request");
        pull.setBody("Description");
        pull.setState(Issue.State.OPEN);
        pull.setAuthor(fixture.author());
        pull.setHtmlUrl(fixture.repository().getHtmlUrl() + "/pull/7");
        pull.setCreatedAt(Instant.now());
        pull.setUpdatedAt(Instant.now());
        pull.setHeadRefOid("a".repeat(40));
        pull.setBaseRefOid("b".repeat(40));
        pull.setHeadRefName("topic");
        pull.setBaseRefName("main");
        var secondAssignee = users.saveAndFlush(TestUserFactory.createUser(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                "assignee-" + UUID.randomUUID(),
                fixture.provider()));
        pull.getAssignees().add(fixture.author());
        pull.getAssignees().add(secondAssignee);
        pull.replaceClosingIssues(Set.of(issue));
        pull = pulls.saveAndFlush(pull);
        ItemRef holder = writer.upsert(new ItemRegistration(
                fixture.workspace().getId(),
                ScmItemEvents.REPOSITORY,
                fixture.repository().getId(),
                null,
                null));
        ItemRef issueRef = writer.upsert(
                new ItemRegistration(holder.workspaceId(), ScmItemEvents.ISSUE, issue.getId(), holder.itemId(), null));
        ItemRef pullRef = writer.upsert(new ItemRegistration(
                holder.workspaceId(), ScmItemEvents.PULL_REQUEST, pull.getId(), holder.itemId(), null));
        var issueSnapshot = sources.pin(issueRef, null);
        var pullSnapshot = sources.pin(pullRef, null);
        assertThat(issueSnapshot.label().noun()).isEqualTo("issue");
        assertThat(issueSnapshot.label().label()).isEqualTo("#7");
        assertThat(pullSnapshot.label().label()).isEqualTo(type == IdentityProviderType.GITLAB ? "!7" : "#7");
        assertThat(issueSnapshot.state()).doesNotContainKey("baseBranch");
        assertThat(pullSnapshot.state()).containsEntry("baseBranch", "main");
        assertThat(pullSnapshot.label().noun())
                .isEqualTo(type == IdentityProviderType.GITLAB ? "merge request" : "pull request");
        assertThat(pullSnapshot.roles()).anySatisfy(role -> {
            assertThat(role.role()).isEqualTo(AboutRole.AUTHOR);
            assertThat(role.identity().providerId())
                    .isEqualTo(fixture.provider().getId());
            assertThat(role.identity().subject())
                    .isEqualTo(Long.toString(fixture.author().getNativeId()));
        });
        assertThat(pullSnapshot.roles().stream()
                        .filter(role -> role.role() == AboutRole.ASSIGNEE)
                        .map(role -> role.identity().subject())
                        .toList())
                .containsExactlyInAnyOrder(
                        Long.toString(fixture.author().getNativeId()), Long.toString(secondAssignee.getNativeId()));
        var named = sources.labels(holder.workspaceId(), List.of(issueRef, pullRef));
        assertThat(named.get(issueRef.itemId())).isEqualTo(issueSnapshot.label());
        assertThat(named.get(pullRef.itemId())).isEqualTo(pullSnapshot.label());
        var expectedBodies = Map.of(issueRef.itemId(), "Issue body", pullRef.itemId(), "Description");
        for (var snapshot : List.of(issueSnapshot, pullSnapshot)) {
            var core = snapshot.item().kind().equals(ScmItemEvents.ISSUE)
                    ? IssueContentSource.CORE
                    : PullRequestContentSource.CORE;
            var selected = snapshot.item().kind().equals(ScmItemEvents.ISSUE)
                    ? Set.of(core, IssueContentSource.COMMENTS)
                    : Set.of(core);
            var captured = sources.capture(
                    snapshot, new CaptureRequest(UUID.randomUUID(), UUID.randomUUID(), 1, selected, "2.0.0", 3));
            if (snapshot.item().kind().equals(ScmItemEvents.ISSUE)) {
                var discussion = new JsonMapper().readTree(captured.files().get("context/comments.json"));
                assertThat(discussion).hasSize(2);
                assertThat(discussion.get(0).path("body").asString()).isEqualTo("first");
                assertThat(discussion.get(1).path("body").asString()).isEqualTo("second");
            }
            assertThat(captured.files()).containsKey("context/metadata.json");
            var staged = sources.record(snapshot, captured.files());
            assertThat(staged.path("title").asString())
                    .isEqualTo(snapshot.label().title());
            assertThat(staged.path("body").asString())
                    .isEqualTo(expectedBodies.get(snapshot.item().itemId()));
            assertThat(staged.path("author").asString())
                    .isEqualTo(fixture.author().getLogin());
            assertThat(staged.path("provider_id").asLong())
                    .isEqualTo(fixture.provider().getId());
            assertThat(staged.path("repository_id").asLong())
                    .isEqualTo(fixture.repository().getId());
            assertThat(captured.citableTexts())
                    .isNotEmpty()
                    .allSatisfy(text -> assertThat(text.sha256())
                            .isEqualTo(ScmVersion.contentHash(
                                    Objects.requireNonNull(captured.files().get(text.stagedPath())))));
        }
        var linked = sources.capture(
                pullSnapshot,
                new CaptureRequest(
                        UUID.randomUUID(), UUID.randomUUID(), 1, Set.of(LinkedWorkItemContentSource.KIND), "2.0.0", 3));
        var linkedWords = Objects.requireNonNull(linked.files().get("context/linked_work_items/7.md"));
        assertThat(new String(linkedWords, StandardCharsets.UTF_8)).contains("An issue", "Issue body");
        assertThat(linked.citableTexts())
                .filteredOn(text -> text.path().equals("context/linked_work_items/7.md"))
                .singleElement()
                .satisfies(text -> {
                    assertThat(text.source()).isEqualTo(LinkedWorkItemContentSource.KIND.value());
                    assertThat(linked.files().get(text.stagedPath())).isEqualTo(linkedWords);
                    assertThat(text.sha256()).isEqualTo(ScmVersion.contentHash(linkedWords));
                });
        var wrongKind = writer.upsert(
                new ItemRegistration(holder.workspaceId(), ScmItemEvents.ISSUE, pull.getId(), holder.itemId(), null));
        assertThatThrownBy(() -> sources.pin(wrongKind, null))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class, e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.ITEM_REMOVED));
        var review = new PullRequestReview();
        review.setNativeId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        review.setProvider(fixture.provider());
        review.setPullRequest(pull);
        review.setAuthor(fixture.author());
        review.setBody("Review words");
        review.setState(PullRequestReview.State.COMMENTED);
        review.setHtmlUrl(pull.getHtmlUrl() + "#review");
        review.setCommitId("a".repeat(40));
        review.setSubmittedAt(Instant.now());
        review = reviews.saveAndFlush(review);
        var reviewRef = writer.upsert(new ItemRegistration(
                holder.workspaceId(), ScmItemEvents.PULL_REQUEST_REVIEW, review.getId(), pullRef.itemId(), null));
        var reviewSnapshot = sources.pin(reviewRef, null);
        assertThat(reviewSnapshot.roles()).extracting(RoleHolder::role).containsExactly(AboutRole.REVIEWER);
        var second = connectedWorkspace(type, fixture.provider().getServerUrl(), "second-" + UUID.randomUUID());
        monitors.saveAndFlush(WorkspaceTestFixtures.repositoryMonitor(
                second, fixture.repository().getNameWithOwner()));
        var context = new EventContext(
                UUID.randomUUID(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                fixture.workspace().getId(),
                null,
                DataSource.WEBHOOK,
                "submitted",
                UUID.randomUUID().toString(),
                type,
                fixture.author().getId());
        var submitted = new ScmDomainEvent.ReviewSubmitted(
                ScmEventPayload.ReviewData.from(review).orElseThrow(), context);
        transactions.executeWithoutResult(status -> publisher.publishEvent(submitted));
        transactions.executeWithoutResult(status -> publisher.publishEvent(submitted));
        for (long workspaceId : List.of(holder.workspaceId(), second.getId())) {
            var repositoryItem = writer.upsert(new ItemRegistration(
                    workspaceId, ScmItemEvents.REPOSITORY, fixture.repository().getId(), null, null));
            var parent = writer.upsert(new ItemRegistration(
                    workspaceId, ScmItemEvents.PULL_REQUEST, pull.getId(), repositoryItem.itemId(), null));
            var child = writer.upsert(new ItemRegistration(
                    workspaceId, ScmItemEvents.PULL_REQUEST_REVIEW, review.getId(), parent.itemId(), null));
            var event = events.findByWorkspaceIdAndItemIdAndNameAndItemVersion(
                            workspaceId,
                            child.itemId(),
                            ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED.value(),
                            sources.pin(child, null).version().value())
                    .orElseThrow();
            assertThat(event.getActorUserId()).isEqualTo(fixture.author().getId());
            assertThat(event.getRequestedById()).isNull();
            assertThat(event.getOccurredAt()).isEqualTo(context.occurredAt());
        }
        var otherFixture = fixture(type);
        monitors.saveAndFlush(WorkspaceTestFixtures.repositoryMonitor(
                otherFixture.workspace(), fixture.repository().getNameWithOwner()));
        var otherRoot = writer.upsert(new ItemRegistration(
                otherFixture.workspace().getId(),
                ScmItemEvents.REPOSITORY,
                otherFixture.repository().getId(),
                null,
                null));
        var wrongIssueParent = writer.upsert(new ItemRegistration(
                otherRoot.workspaceId(), ScmItemEvents.ISSUE, issue.getId(), otherRoot.itemId(), null));
        var wrongPullParent = writer.upsert(new ItemRegistration(
                otherRoot.workspaceId(), ScmItemEvents.PULL_REQUEST, pull.getId(), otherRoot.itemId(), null));
        var wrongReviewAncestor = writer.upsert(new ItemRegistration(
                otherRoot.workspaceId(),
                ScmItemEvents.PULL_REQUEST_REVIEW,
                review.getId(),
                wrongPullParent.itemId(),
                null));
        for (var misparented : List.of(wrongIssueParent, wrongPullParent, wrongReviewAncestor)) {
            assertThatThrownBy(() -> sources.pin(misparented, null))
                    .isInstanceOfSatisfying(
                            ItemCaptureException.class,
                            failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.ITEM_NOT_VISIBLE));
            assertThat(sourceAccess.permits(misparented, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW))
                    .isFalse();
        }
        assertThat(reviewSnapshot.state()).containsEntry("baseBranch", "main");
        var historyAuthor = users.saveAndFlush(TestUserFactory.createUser(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                "history-" + UUID.randomUUID(),
                fixture.provider()));
        var historicalCommit = new Commit();
        historicalCommit.setSha("e".repeat(40));
        historicalCommit.setMessage("Earlier repository work");
        historicalCommit.setAuthoredAt(Instant.now());
        historicalCommit.setCommittedAt(Instant.now());
        historicalCommit.setRepository(fixture.repository());
        historicalCommit.setAuthor(historyAuthor);
        commits.saveAndFlush(historicalCommit);
        UUID restriction = databaseTestUtils.erase(
                Objects.requireNonNull(fixture.provider().getId()), Long.toString(historyAuthor.getNativeId()), "");
        try {
            assertThatThrownBy(() -> sources.capture(
                            pullSnapshot,
                            new CaptureRequest(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    1,
                                    Set.of(RepositoryTreeContentSource.KIND),
                                    "2.0.0",
                                    3)))
                    .isInstanceOfSatisfying(
                            ItemCaptureException.class,
                            failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.CONSENT_WITHHELD));
        } finally {
            jdbc.update("DELETE FROM identity_restriction WHERE id=?", restriction);
        }
        assertThat(mergeTimes.mergedAt(holder.workspaceId(), List.of(issueRef, pullRef, reviewRef)))
                .isEmpty();
        Instant mergedAt = Instant.parse("2026-10-14T10:00:00Z");
        pull.setMerged(true);
        pull.setMergedAt(mergedAt);
        pulls.saveAndFlush(pull);
        assertThat(mergeTimes.mergedAt(holder.workspaceId(), List.of(issueRef, pullRef, reviewRef)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(pullRef.itemId(), mergedAt, reviewRef.itemId(), mergedAt));
        assertThatThrownBy(() -> mergeTimes.mergedAt(holder.workspaceId() + 1, List.of(pullRef)))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.ITEM_NOT_VISIBLE));
        pull.setMergedAt(null);
        pulls.saveAndFlush(pull);
        assertThat(mergeTimes.mergedAt(holder.workspaceId(), List.of(pullRef, reviewRef)))
                .isEmpty();
        pull.setMergedAt(mergedAt);
        pulls.saveAndFlush(pull);
        var timingMonitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        holder.workspaceId(), fixture.repository().getNameWithOwner())
                .orElseThrow();
        timingMonitor.setNativeId(fixture.repository().getNativeId() == 13L ? 14L : 13L);
        monitors.saveAndFlush(timingMonitor);
        assertThatThrownBy(() -> mergeTimes.mergedAt(holder.workspaceId(), List.of(pullRef)))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.ITEM_NOT_VISIBLE));
        timingMonitor.setNativeId(fixture.repository().getNativeId());
        monitors.saveAndFlush(timingMonitor);
        writer.remove(holder.workspaceId(), pullRef.itemId(), Instant.now());
        assertThat(mergeTimes.mergedAt(holder.workspaceId(), List.of(pullRef, reviewRef)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(pullRef.itemId(), mergedAt, reviewRef.itemId(), mergedAt));
        writer.restore(holder.workspaceId(), pullRef.itemId());
        var missingTiming = writer.upsert(new ItemRegistration(
                holder.workspaceId(), ScmItemEvents.PULL_REQUEST, Long.MAX_VALUE - 1, holder.itemId(), null));
        assertThatThrownBy(() -> mergeTimes.mergedAt(holder.workspaceId(), List.of(missingTiming)))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.SOURCE_NOT_READY));

        var changed = pulls.findById(pull.getId()).orElseThrow();
        changed.setBaseRefOid("c".repeat(40));
        pulls.saveAndFlush(changed);
        assertThat(sources.pin(pullRef, null).version()).isNotEqualTo(pullSnapshot.version());
        assertThatThrownBy(() -> sources.pin(pullRef, pullSnapshot.version()))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.VERSION_UNAVAILABLE));
        var foreign = workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspace("foreign-" + UUID.randomUUID()));
        var spoof = new ItemRef(
                foreign.getId(), issueRef.itemId(), issueRef.kind(), issueRef.sourceId(), issueRef.parentId(), null);
        assertThat(sources.current(spoof)).isEmpty();
        writer.remove(holder.workspaceId(), issueRef.itemId(), Instant.now());
        var removedLabel = Objects.requireNonNull(
                sources.labels(holder.workspaceId(), List.of(issueRef)).get(issueRef.itemId()));
        assertThat(removedLabel.title()).isEqualTo("An issue");
        assertThat(removedLabel.url()).isNull();
        assertThat(sources.current(issueRef)).isEmpty();
        writer.restore(holder.workspaceId(), issueRef.itemId());
        assertThat(sources.current(issueRef)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldKeepTheItemWithoutAnEventUntilItsProviderVersionCanBeProved(IdentityProviderType type) {
        var fixture = fixture(type);
        var pull = new PullRequest();
        pull.setNativeId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        pull.setProvider(fixture.provider());
        pull.setRepository(fixture.repository());
        pull.setNumber(7);
        pull.setTitle("A pull request");
        pull.setBody("Description");
        pull.setState(Issue.State.OPEN);
        pull.setAuthor(fixture.author());
        pull.setHtmlUrl(fixture.repository().getHtmlUrl() + "/pull/7");
        pull.setCreatedAt(Instant.now());
        pull.setUpdatedAt(Instant.now());
        pull.setBaseRefOid("b".repeat(40));
        pull.setHeadRefName("topic");
        pull.setBaseRefName("main");
        pull = pulls.saveAndFlush(pull);
        var context = EventContext.forSync(fixture.workspace().getId(), null, type, Instant.now());
        long pullId = pull.getId();
        transactions.executeWithoutResult(status -> {
            var current = pulls.findById(pullId).orElseThrow();
            publisher.publishEvent(
                    new ScmDomainEvent.PullRequestCreated(ScmEventPayload.PullRequestData.from(current), context));
        });
        var holder = writer.upsert(new ItemRegistration(
                fixture.workspace().getId(),
                ScmItemEvents.REPOSITORY,
                fixture.repository().getId(),
                null,
                null));
        var item = writer.upsert(new ItemRegistration(
                fixture.workspace().getId(), ScmItemEvents.PULL_REQUEST, pull.getId(), holder.itemId(), null));
        assertThat(jdbc.queryForList(
                        "SELECT item_version FROM item_event WHERE workspace_id=? AND item_id=?",
                        String.class,
                        item.workspaceId(),
                        item.itemId()))
                .isEmpty();
        assertThat(jdbc.queryForList(
                        "SELECT (removed_at IS NULL)::text FROM item WHERE workspace_id=? AND id=?",
                        String.class,
                        item.workspaceId(),
                        item.itemId()))
                .containsExactly("true");
        pull.setHeadRefOid("a".repeat(40));
        pulls.saveAndFlush(pull);
        var captured = sources.pin(item, null).version();
        var event = Objects.requireNonNull(transactions.execute(status -> {
            var current = pulls.findById(pullId).orElseThrow();
            var published =
                    new ScmDomainEvent.PullRequestCreated(ScmEventPayload.PullRequestData.from(current), context);
            publisher.publishEvent(published);
            return published;
        }));
        transactions.executeWithoutResult(status -> publisher.publishEvent(event));
        assertThat(jdbc.queryForList(
                        "SELECT name || '|' || item_version || '|' || found_by FROM item_event WHERE workspace_id=?"
                                + " AND item_id=?",
                        String.class,
                        item.workspaceId(),
                        item.itemId()))
                .containsExactly("scm.pull_request.opened|" + captured.value() + "|SYNC");
    }

    private Fixture fixture(IdentityProviderType type) {
        String slug = "scm-source-" + UUID.randomUUID();
        String url = type == IdentityProviderType.GITHUB ? "https://github.com" : "https://gitlab.example.test";
        var provider = providers
                .findByTypeAndServerUrl(type, url)
                .orElseGet(() -> providers.saveAndFlush(new IdentityProvider(type, url)));
        Workspace workspace = type == IdentityProviderType.GITHUB
                ? WorkspaceTestFixtures.persistInstallationWorkspace(
                        workspaces,
                        connections,
                        WorkspaceTestFixtures.installationWorkspace(
                                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), slug),
                        ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE))
                : WorkspaceTestFixtures.persistGitLabWorkspace(
                        workspaces,
                        connections,
                        WorkspaceTestFixtures.gitLabPatWorkspace(slug).withSlug(slug),
                        url);
        var repository = new Repository();
        repository.setNativeId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        repository.setProvider(provider);
        repository.setName("source");
        repository.setNameWithOwner(slug + "/source");
        repository.setHtmlUrl(url + "/" + slug + "/source");
        repository.setDefaultBranch("main");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setPushedAt(Instant.now());
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository = repositories.saveAndFlush(repository);
        monitors.saveAndFlush(WorkspaceTestFixtures.repositoryMonitor(workspace, repository.getNameWithOwner()));
        var author = users.saveAndFlush(TestUserFactory.createUser(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), slug + "-author", provider));
        return new Fixture(workspace, provider, repository, author);
    }

    private record Fixture(Workspace workspace, IdentityProvider provider, Repository repository, User author) {}

    private Workspace connectedWorkspace(IdentityProviderType type, String url, String slug) {
        return type == IdentityProviderType.GITHUB
                ? WorkspaceTestFixtures.persistInstallationWorkspace(
                        workspaces,
                        connections,
                        WorkspaceTestFixtures.installationWorkspace(
                                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), slug),
                        ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE))
                : WorkspaceTestFixtures.persistGitLabWorkspace(
                        workspaces,
                        connections,
                        WorkspaceTestFixtures.gitLabPatWorkspace(slug).withSlug(slug),
                        url);
    }
}
