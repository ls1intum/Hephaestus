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
import de.tum.cit.aet.hephaestus.integration.core.events.BotCommandReceivedEvent;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEvent;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
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
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabHeadPipeline;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabMergeRequestProcessor;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.pullrequest.GitLabMergeRequestReadinessReader;
import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Autowired
    GitLabMergeRequestProcessor mergeRequests;

    @Autowired
    IssueRepository issueRows;

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

    @ParameterizedTest
    @CsvSource({"GITHUB,false", "GITHUB,true", "GITLAB,false", "GITLAB,true"})
    void shouldSelectANotDraftPracticeOnlyForReadyWork(IdentityProviderType type, boolean draft) {
        var prepared = prepare(type, false, Map.of("draftStatus", Set.of("NOT_DRAFT")), draft);
        publish(prepared);
        var event = event(prepared);
        var admitted = reviews.findByWorkspaceIdAndEventId(prepared.item().workspaceId(), event.getId());
        if (draft) {
            assertThat(event.getState()).isEqualTo(ItemEventState.WITHHELD);
            assertThat(event.getReason()).isEqualTo(ReviewRequestReason.NO_ACTIVE_PRACTICE);
            assertThat(admitted).isEmpty();
        } else {
            assertThat(event.getState()).isEqualTo(ItemEventState.STARTED);
            assertThat(admitted).isPresent();
        }
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

    @Test
    void shouldKeepAGitLabOccasionUntilReadinessRecordsTheDiffBaseItWasMissing() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var pull = withoutBase(prepared);
        publish(prepared);
        var pending = onlyEvent(prepared);
        assertThat(pending.getItemVersion()).isNotEqualTo(prepared.version().value());
        assertThat(pending.getState()).isEqualTo(ItemEventState.RECORDED);
        assertThat(itemReviews(prepared)).isEmpty();

        assertThat(readiness(pull, new GitLabMergeRequestReadinessReader.DiffRefs("b".repeat(40), "a".repeat(40))))
                .isTrue();

        var resolved = events.findById(pending.getId()).orElseThrow();
        assertThat(resolved.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(resolved.getState()).isEqualTo(ItemEventState.STARTED);
        var admitted = reviews.findByWorkspaceIdAndEventId(prepared.item().workspaceId(), pending.getId())
                .orElseThrow();
        assertThat(admitted.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(admitted.getBaseVersion()).isEqualTo("a".repeat(40));
    }

    @Test
    void shouldWithholdAPendingOccasionWhenTheHeadMovesBeforeItsDiffBaseIsKnown() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var pull = withoutBase(prepared);
        publish(prepared);
        var pending = onlyEvent(prepared);
        transactions.executeWithoutResult(ignored -> {
            var moved = pulls.findById(pull.getId()).orElseThrow();
            moved.setHeadRefOid("c".repeat(40));
            moved.setBaseRefOid("a".repeat(40));
            publisher.publishEvent(new ScmDomainEvent.PullRequestSynchronized(
                    ScmEventPayload.PullRequestData.from(pulls.saveAndFlush(moved)), context(prepared)));
        });

        var withheld = events.findById(pending.getId()).orElseThrow();
        assertThat(withheld.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(withheld.getReason()).isEqualTo(ReviewRequestReason.VERSION_SUPERSEDED);
        assertThat(itemReviews(prepared)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "body"})
    void shouldWithholdAPendingOccasionWhenItsTextChangesBeforeItsDiffBaseIsKnown(String field) {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var pull = withoutBase(prepared);
        publish(prepared);
        var pending = onlyEvent(prepared);
        transactions.executeWithoutResult(ignored -> {
            var edited = pulls.findById(pull.getId()).orElseThrow();
            if (field.equals("title")) edited.setTitle("A retitled change");
            else edited.setBody("A rewritten description");
            publisher.publishEvent(new ScmDomainEvent.PullRequestUpdated(
                    ScmEventPayload.PullRequestData.from(pulls.saveAndFlush(edited)),
                    Set.of(field),
                    context(prepared)));
        });

        assertThat(readiness(pull, new GitLabMergeRequestReadinessReader.DiffRefs("b".repeat(40), "a".repeat(40))))
                .isTrue();

        var withheld = events.findById(pending.getId()).orElseThrow();
        assertThat(withheld.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(withheld.getReason()).isEqualTo(ReviewRequestReason.VERSION_SUPERSEDED);
        assertThat(withheld.getItemVersion()).isNull();
        var edit = jdbc.queryForObject(
                "SELECT item_version FROM item_event WHERE workspace_id=? AND item_id=? AND name=?",
                String.class,
                prepared.item().workspaceId(),
                prepared.item().itemId(),
                ScmItemEvents.PULL_REQUEST_EDITED.value());
        assertThat(edit)
                .isEqualTo(source.pin(prepared.item(), null).version().value())
                .isNotEqualTo(prepared.version().value());
        assertThat(itemReviews(prepared)).isEmpty();
    }

    @Test
    void shouldWithholdAPendingOccasionWhenAClosedIssueChangesBeforeItsDiffBaseIsKnown() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        long pullId = Objects.requireNonNull(prepared.item().sourceId());
        long issueId = Objects.requireNonNull(transactions.execute(ignored -> {
            var pull = pulls.findById(pullId).orElseThrow();
            var issue = new Issue();
            issue.setNativeId(nativeId());
            issue.setProvider(pull.getProvider());
            issue.setRepository(pull.getRepository());
            issue.setNumber(4);
            issue.setTitle("The problem");
            issue.setBody("What is wrong");
            issue.setState(Issue.State.OPEN);
            issue.setAuthor(pull.getAuthor());
            issue.setHtmlUrl(pull.getHtmlUrl() + "/issues/4");
            issue.setCreatedAt(Instant.now());
            issue.setUpdatedAt(Instant.now());
            issue = issueRows.saveAndFlush(issue);
            pull.replaceClosingIssues(Set.of(issue));
            pulls.saveAndFlush(pull);
            return issue.getId();
        }));
        withoutBase(prepared);
        publish(prepared);
        var pending = onlyEvent(prepared);
        transactions.executeWithoutResult(ignored -> {
            var issue = issueRows.findById(issueId).orElseThrow();
            issue.setBody("What is wrong, restated");
            publisher.publishEvent(new ScmDomainEvent.IssueUpdated(
                    ScmEventPayload.IssueData.from(issueRows.saveAndFlush(issue)), Set.of("body"), context(prepared)));
        });

        var withheld = events.findById(pending.getId()).orElseThrow();
        assertThat(withheld.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(withheld.getReason()).isEqualTo(ReviewRequestReason.VERSION_SUPERSEDED);
        assertThat(itemReviews(prepared)).isEmpty();
    }

    @Test
    void shouldKeepOnePendingOccasionAndOneReviewWhenTheHookIsRedelivered() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var pull = withoutBase(prepared);
        for (int delivery = 0; delivery < 3; delivery++) publish(prepared);
        var pending = onlyEvent(prepared);
        assertThat(pending.getItemVersion()).isNull();

        assertThat(readiness(pull, new GitLabMergeRequestReadinessReader.DiffRefs("b".repeat(40), "a".repeat(40))))
                .isTrue();
        publish(prepared);

        var resolved = onlyEvent(prepared);
        assertThat(resolved.getId()).isEqualTo(pending.getId());
        assertThat(resolved.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(resolved.getState()).isEqualTo(ItemEventState.STARTED);
        assertThat(itemReviews(prepared)).singleElement().satisfies(review -> {
            assertThat(review.getOrigin()).isEqualTo(ReviewOrigin.EVENT);
            assertThat(review.getItemVersion()).isEqualTo(prepared.version().value());
        });
    }

    @Test
    void shouldStartOneRequestedReviewWhenTheSameCommandNoteArrivesThreeTimes() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var author = Objects.requireNonNull(transactions.execute(ignored -> users.findById(
                        Objects.requireNonNull(pulls.findById(Objects.requireNonNull(
                                                prepared.item().sourceId()))
                                        .orElseThrow()
                                        .getAuthor())
                                .getId())
                .orElseThrow()));
        long noteId = nativeId();
        for (int delivery = 0; delivery < 3; delivery++) command(prepared, author, noteId);

        assertThat(itemReviews(prepared)).singleElement().satisfies(review -> {
            assertThat(review.getOrigin()).isEqualTo(ReviewOrigin.REQUEST);
            assertThat(review.getRequestedById()).isEqualTo(account(author));
            assertThat(review.getItemVersion()).isEqualTo(prepared.version().value());
        });
    }

    @Test
    void shouldStartNoReviewWhenACommenterHasNoStandingOnTheWork() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.GITLAB, "https://gitlab.example.test")
                .orElseThrow();
        var colleague =
                users.saveAndFlush(TestUserFactory.createUser(nativeId(), "colleague-" + UUID.randomUUID(), provider));
        TestUserFactory.ensureAccountForUser(accounts, identities, colleague);
        jdbc.update(
                "INSERT INTO membership(workspace_id,account_id,role,joined_at) VALUES (?,?,'MEMBER',now())",
                prepared.item().workspaceId(),
                account(colleague));

        command(prepared, colleague, nativeId());

        assertThat(itemReviews(prepared)).isEmpty();
    }

    private PullRequest withoutBase(Prepared prepared) {
        long id = Objects.requireNonNull(prepared.item().sourceId());
        // A merge request hook stores the head before GitLab has paired a diff base with it.
        jdbc.update("UPDATE issue SET base_ref_oid = NULL WHERE id = ?", id);
        return pulls.findById(id).orElseThrow();
    }

    private ItemEvent onlyEvent(Prepared prepared) {
        var ids = jdbc.queryForList(
                "SELECT id FROM item_event WHERE workspace_id=? AND item_id=? AND name=?",
                UUID.class,
                prepared.item().workspaceId(),
                prepared.item().itemId(),
                prepared.name().value());
        assertThat(ids).hasSize(1);
        return events.findById(ids.getFirst()).orElseThrow();
    }

    private List<Review> itemReviews(Prepared prepared) {
        return reviews.findByWorkspaceIdAndItemIdOrderByOrderedAtDesc(
                prepared.item().workspaceId(), prepared.item().itemId());
    }

    private EventContext context(Prepared prepared) {
        return ((ScmDomainEvent.ContextualEvent) prepared.event()).context();
    }

    private boolean readiness(PullRequest pull, GitLabMergeRequestReadinessReader.DiffRefs diffRefs) {
        var repository = repositories
                .findById(Objects.requireNonNull(pull.getRepository()).getId())
                .orElseThrow();
        var facts = new GitLabMergeRequestReadinessReader.Facts(
                repository.getNativeId(),
                pull.getNativeId(),
                "opened",
                Instant.now(),
                Objects.requireNonNull(pull.getHeadRefOid()),
                true,
                "mergeable",
                null,
                GitLabHeadPipeline.NO_PIPELINE,
                List.of(),
                null,
                GitLabMergeRequestReadinessReader.Merge.UNKNOWN,
                null,
                diffRefs);
        return mergeRequests.applyReadiness(
                repository,
                pull.getNumber(),
                facts,
                Instant.now(),
                ProcessingContext.forWebhook(repository.getId(), repository, "update"));
    }

    private void command(Prepared prepared, User commenter, long noteId) {
        transactions.executeWithoutResult(ignored -> publisher.publishEvent(new BotCommandReceivedEvent(
                IntegrationKind.GITLAB,
                prepared.item().workspaceId(),
                prepared.item().itemId(),
                "/hephaestus review",
                commenter.getLogin(),
                providerId(commenter),
                commenter.getNativeId(),
                noteId)));
    }

    private long account(User user) {
        return Objects.requireNonNull(transactions.execute(ignored -> identities
                .findActiveByProviderSubject(providerId(user), Long.toString(user.getNativeId()), null)
                .orElseThrow()
                .getAccount()
                .getId()));
    }

    private long providerId(User user) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT provider_id FROM \"user\" WHERE id = ?", Long.class, user.getId()));
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
        return prepare(type, reviewed, Map.of(), false);
    }

    private Prepared prepare(
            IdentityProviderType type, boolean reviewed, Map<String, Set<String>> state, boolean draft) {
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
                new ReviewOn(List.of(name), state),
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
        pull.setDraft(draft);
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
