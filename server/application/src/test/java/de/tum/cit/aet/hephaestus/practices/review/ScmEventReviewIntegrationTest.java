package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEvent;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemService;
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
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.FrozenModel;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class ScmEventReviewIntegrationTest extends AbstractResultAdmissionIntegrationTest {
    @Autowired
    IdentityProviderRepository providers;

    @Autowired
    ConnectionRepository connections;

    @Autowired
    WorkspaceRepository workspaces;

    @Autowired
    RepositoryToMonitorRepository monitors;

    @Autowired
    RepositoryRepository repositories;

    @Autowired
    UserRepository users;

    @Autowired
    PullRequestRepository pulls;

    @Autowired
    PullRequestReviewRepository providerReviews;

    @Autowired
    AccountRepository accounts;

    @Autowired
    IdentityRepository identities;

    @Autowired
    PracticeVersionService versions;

    @Autowired
    ItemIndexService writer;

    @Autowired
    ScmItemService source;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ItemEventRepository events;

    @Autowired
    ReviewRepository reviews;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReviewService service;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        when(budget.canRequest(anyLong(), eq(ReviewKind.ITEM), isNull())).thenReturn(true);
        when(models.models(anyLong(), anyList()))
                .thenReturn(List.of(
                        new BoundModelSlot("chat", ModelKind.CHAT, null),
                        new BoundModelSlot("decision", ModelKind.DECISION, null),
                        new BoundModelSlot("embedding", ModelKind.EMBEDDING, null),
                        new BoundModelSlot("reranking", ModelKind.RERANKING, null),
                        new BoundModelSlot(
                                "review",
                                ModelKind.CHAT,
                                new FrozenModel("instance:1", "OPENAI_COMPLETIONS", "test-model", null, Map.of()))));
    }

    @ParameterizedTest
    @CsvSource({"GITHUB,false", "GITHUB,true", "GITLAB,false", "GITLAB,true"})
    void shouldRestoreTheCanonicalVersionThroughTheRecordedEventListener(IdentityProviderType type, boolean review) {
        var prepared = prepare(type, review);
        publish(prepared);
        var event = event(prepared);
        assertThat(event.getState()).isEqualTo(ItemEventState.STARTED);
        var admitted = reviews.findByWorkspaceIdAndEventId(prepared.item().workspaceId(), event.getId())
                .orElseThrow();
        assertThat(admitted.getOrigin()).isEqualTo(ReviewOrigin.EVENT);
        assertThat(admitted.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(admitted.getBaseVersion()).isEqualTo(prepared.version().baseVersion());
        assertThat(admitted.getStatus()).isEqualTo(ReviewStatus.QUEUED);
        publish(prepared);
        assertThat(reviews.findByWorkspaceIdAndItemIdOrderByOrderedAtDesc(
                        prepared.item().workspaceId(), prepared.item().itemId()))
                .extracting(row -> row.getId())
                .containsExactly(admitted.getId());
        assertThat(source.pin(prepared.item(), prepared.version()).version()).isEqualTo(prepared.version());
        assertThatThrownBy(() -> source.pin(
                        prepared.item(), new ItemVersion(prepared.version().value(), "c".repeat(40))))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.VERSION_UNAVAILABLE));
        long pullId = review
                ? Objects.requireNonNull(providerReviews
                                .findById(Objects.requireNonNull(prepared.item().sourceId()))
                                .orElseThrow()
                                .getPullRequest())
                        .getId()
                : Objects.requireNonNull(prepared.item().sourceId());
        var changed = pulls.findById(pullId).orElseThrow();
        changed.setBody("A changed description");
        pulls.saveAndFlush(changed);
        assertThatThrownBy(() -> source.pin(
                        prepared.item(), new ItemVersion(prepared.version().value(), null)))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.VERSION_UNAVAILABLE));
    }

    @Test
    void shouldWithholdAutomaticEventsBeforePinningWhenActivityReviewsAreOff() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var workspace = workspaces.findById(prepared.item().workspaceId()).orElseThrow();
        workspace.getFeatures().setPracticeReviewAutoTriggerEnabled(false);
        workspaces.saveAndFlush(workspace);
        publish(prepared);
        var event = event(prepared);
        assertThat(event.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(event.getReason()).isEqualTo(ReviewRequestReason.GATE_WITHHELD);
        assertThat(event.getRetryAt()).isNull();
        assertThat(reviews.findByWorkspaceIdAndEventId(prepared.item().workspaceId(), event.getId()))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = ReviewOrigin.class,
            names = {"REQUEST", "BACKFILL", "SCHEDULE"})
    void shouldStopRequestedScansBeforeCaptureWhenRequestsAreOff(ReviewOrigin origin) {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var workspace = workspaces.findById(prepared.item().workspaceId()).orElseThrow();
        workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
        workspaces.saveAndFlush(workspace);
        long account = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT account_id FROM membership WHERE workspace_id=?", Long.class, workspace.getId()));
        var request = ReviewRequest.builder()
                .workspaceId(workspace.getId())
                .itemId(Objects.requireNonNull(prepared.item().parentId()))
                .kind(ReviewKind.SCAN)
                .origin(origin)
                .requestedById(origin == ReviewOrigin.REQUEST ? account : null)
                .batchId(origin == ReviewOrigin.REQUEST ? null : UUID.randomUUID())
                .requestKey(UUID.randomUUID())
                .requestDigest("c".repeat(64))
                .practiceIds(List.of())
                .build();
        assertThatThrownBy(() -> service.request(request))
                .isInstanceOfSatisfying(
                        ReviewRequestRejected.class,
                        refusal -> assertThat(refusal.eventReason()).isEqualTo(ReviewRequestReason.GATE_WITHHELD));
    }

    private ItemEvent event(Prepared prepared) {
        return events.findByWorkspaceIdAndItemIdAndNameAndItemVersion(
                        prepared.item().workspaceId(),
                        prepared.item().itemId(),
                        prepared.name().value(),
                        prepared.version().value())
                .orElseThrow();
    }

    private void publish(Prepared prepared) {
        transactions.executeWithoutResult(ignored -> publisher.publishEvent(prepared.event()));
    }

    private Prepared prepare(IdentityProviderType type, boolean reviewed) {
        var fixture = fixture(type);
        var workspace = fixture.workspace();
        workspace.getFeatures().setPracticesEnabled(true);
        workspaces.saveAndFlush(workspace);
        TestUserFactory.ensureAccountForUser(accounts, identities, fixture.author());
        long account = Objects.requireNonNull(identities
                .findActiveByProviderSubject(
                        Objects.requireNonNull(fixture.provider().getId()),
                        Long.toString(fixture.author().getNativeId()),
                        null)
                .orElseThrow()
                .getAccount()
                .getId());
        jdbc.update(
                "INSERT INTO membership(workspace_id,account_id,role,joined_at) VALUES (?,?,'OWNER',now())",
                workspace.getId(),
                account);
        var kind = reviewed ? ScmItemEvents.PULL_REQUEST_REVIEW : ScmItemEvents.PULL_REQUEST;
        var name = reviewed ? ScmItemEvents.PULL_REQUEST_REVIEW_SUBMITTED : ScmItemEvents.PULL_REQUEST_OPENED;
        var original = DefinitionTestFixtures.definition(kind, "Describe the work", "Explain the work.", null);
        var definition = new PracticeDefinition(
                original.name(),
                new ReviewOn(List.of(name), Map.of()),
                original.appliesWhen(),
                reviewed ? AboutRole.REVIEWER : AboutRole.AUTHOR,
                original.reads(),
                original.reviewedBy(),
                original.criteria(),
                original.script(),
                original.scan(),
                original.lineNotes(),
                original.guidance());
        versions.create(
                new PracticeVersionService.NewPractice(workspace, "event-test", kind, definition, null, 0, account));
        var pull = new PullRequest();
        pull.setNativeId(nativeId());
        pull.setProvider(fixture.provider());
        pull.setRepository(fixture.repository());
        pull.setNumber(3);
        pull.setTitle("A bounded change");
        pull.setBody("Describe this change");
        pull.setState(Issue.State.OPEN);
        pull.setAuthor(fixture.author());
        pull.setHtmlUrl(fixture.repository().getHtmlUrl() + "/pull/3");
        pull.setCreatedAt(Instant.now());
        pull.setUpdatedAt(Instant.now());
        pull.setBaseRefName("main");
        pull.setHeadRefName("topic");
        pull.setBaseRefOid("a".repeat(40));
        pull.setHeadRefOid("b".repeat(40));
        pull = pulls.saveAndFlush(pull);
        var holder = writer.upsert(new ItemRegistration(
                workspace.getId(),
                ScmItemEvents.REPOSITORY,
                fixture.repository().getId(),
                null,
                null));
        var ref = writer.upsert(new ItemRegistration(
                workspace.getId(), ScmItemEvents.PULL_REQUEST, pull.getId(), holder.itemId(), null));
        var context = new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                workspace.getId(),
                null,
                DataSource.WEBHOOK,
                "created",
                UUID.randomUUID().toString(),
                type,
                fixture.author().getId());
        ScmDomainEvent.Event event;
        if (reviewed) {
            var review = new PullRequestReview();
            review.setProvider(fixture.provider());
            review.setNativeId(nativeId());
            review.setPullRequest(pull);
            review.setAuthor(fixture.author());
            review.setBody("Review this change");
            review.setState(PullRequestReview.State.COMMENTED);
            review.setCommitId(pull.getHeadRefOid());
            review.setSubmittedAt(Instant.now());
            review.setHtmlUrl(pull.getHtmlUrl() + "#review");
            review = providerReviews.saveAndFlush(review);
            ref = writer.upsert(new ItemRegistration(
                    workspace.getId(), ScmItemEvents.PULL_REQUEST_REVIEW, review.getId(), ref.itemId(), null));
            event = new ScmDomainEvent.ReviewSubmitted(
                    ScmEventPayload.ReviewData.from(review).orElseThrow(), context);
        } else {
            event = new ScmDomainEvent.PullRequestCreated(ScmEventPayload.PullRequestData.from(pull), context);
        }
        return new Prepared(ref, source.pin(ref, null).version(), name, event);
    }

    private static long nativeId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private record Prepared(ItemRef item, ItemVersion version, ItemEventName name, ScmDomainEvent.Event event) {}

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
        var monitor = WorkspaceTestFixtures.repositoryMonitor(workspace, repository.getNameWithOwner());
        monitor.setNativeId(repository.getNativeId());
        monitors.saveAndFlush(monitor);
        var author = users.saveAndFlush(TestUserFactory.createUser(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), slug + "-author", provider));
        return new Fixture(workspace, provider, repository, author);
    }

    private record Fixture(Workspace workspace, IdentityProvider provider, Repository repository, User author) {}
}
