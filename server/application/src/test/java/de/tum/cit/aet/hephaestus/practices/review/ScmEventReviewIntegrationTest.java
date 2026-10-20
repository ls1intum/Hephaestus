package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemEventRecorded;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemSourceObserved;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.events.BotCommandReceivedEvent;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
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
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
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
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPublicationContext;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackStoreService;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackSendTransactions;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.AdmissionReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.BoundModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionTrace;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.FrozenModel;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@RecordApplicationEvents
class ScmEventReviewIntegrationTest extends AbstractResultAdmissionIntegrationTest {
    @Autowired
    ApplicationEvents applicationEvents;

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

    @Autowired
    ReviewIntegrationData resultData;

    @Autowired
    ReviewAdmissionData admissions;

    @Autowired
    ResultStore resultStore;

    @Autowired
    ResultRepository resultRows;

    @Autowired
    ItemRepository itemRows;

    @Autowired
    PracticeRepository practices;

    @Autowired
    FeedbackStoreService feedbackStore;

    @Autowired
    FeedbackPublicationContext publication;

    @Autowired
    FeedbackSendTransactions delivery;

    @Autowired
    FeedbackRepository feedbackRows;

    @Autowired
    FeedbackCopyRepository copyRows;

    @Autowired
    FeedbackSendRepository sends;

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

    @AfterEach
    void restoreInstanceDeliveryBrake() {
        jdbc.update("UPDATE instance_settings SET silent_mode_engaged=true WHERE id=1");
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldKeepALateOldCaptureAsHistoryWithoutMakingItCurrent(IdentityProviderType type) {
        var prepared = prepare(type, false);
        var item = prepared.item();
        var pull = pulls.findById(Objects.requireNonNull(item.sourceId())).orElseThrow();
        var author =
                users.findById(Objects.requireNonNull(pull.getAuthor()).getId()).orElseThrow();
        var scenario = new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(author)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
        var pending = admissions.pending(
                scenario,
                Instant.now().minusSeconds(30),
                scenario.account().getId(),
                prepared.version().value());
        running(pending);
        var context = new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                item.workspaceId(),
                null,
                DataSource.WEBHOOK,
                "edited",
                UUID.randomUUID().toString(),
                type,
                author.getId());
        transactions.executeWithoutResult(ignored -> {
            var changed = pulls.findById(pull.getId()).orElseThrow();
            changed.setBody("The work changed after this capture.");
            pulls.saveAndFlush(changed);
            publisher.publishEvent(new ScmDomainEvent.PullRequestUpdated(
                    ScmEventPayload.PullRequestData.from(changed), Set.of("body"), context));
        });
        var canonical = source.pin(item, null).version();
        assertThat(canonical).isNotEqualTo(prepared.version());
        var incumbent = resultData.result(
                scenario, ResultOutcome.MET, Instant.now().minusSeconds(60), Instant.now(), canonical.value());

        var receipt = resultStore.admit(
                item.workspaceId(),
                pending.review().getId(),
                pending.jobId(),
                1,
                List.of(admissions.candidate(pending, ResultOutcome.MET)));

        assertThat(receipt.resultIds()).hasSize(1);
        var historical = resultRows.findById(receipt.resultIds().getFirst()).orElseThrow();
        assertThat(historical.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(historical.getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
        assertThat(resultRows.findById(incumbent.getId()).orElseThrow().getEndedAt())
                .isNull();
        assertThat(resultRows.forDevelopersSince(
                        item.workspaceId(), List.of(scenario.account().getId()), Instant.EPOCH, true))
                .extracting(row -> row.getId())
                .doesNotContain(historical.getId());
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldCommitNormalClaimOrderingWhenTheCurrentSourceIsNotReady(IdentityProviderType type) {
        var prepared = prepare(type, false);
        var item = prepared.item();
        var pull = pulls.findById(Objects.requireNonNull(item.sourceId())).orElseThrow();
        var author =
                users.findById(Objects.requireNonNull(pull.getAuthor()).getId()).orElseThrow();
        var scenario = new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(author)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
        var older = admissions.pending(
                scenario,
                Instant.now().minusSeconds(60),
                scenario.account().getId(),
                prepared.version().value());
        var newer = admissions.pending(
                scenario,
                Instant.now(),
                scenario.account().getId(),
                prepared.version().value());
        running(newer);
        var incumbent = resultStore
                .admit(
                        item.workspaceId(),
                        newer.review().getId(),
                        newer.jobId(),
                        1,
                        List.of(admissions.candidate(newer, ResultOutcome.MET)))
                .resultIds()
                .getFirst();
        jdbc.update("UPDATE issue SET base_ref_oid=NULL WHERE id=?", pull.getId());
        assertThatThrownBy(() -> source.current(item)).isInstanceOf(ItemCaptureException.class);
        running(older);

        var historical = resultStore
                .admit(
                        item.workspaceId(),
                        older.review().getId(),
                        older.jobId(),
                        1,
                        List.of(admissions.candidate(older, ResultOutcome.MET)))
                .resultIds()
                .getFirst();

        assertThat(resultRows.findById(historical).orElseThrow().getEndReason()).isEqualTo(ResultEndReason.OUTRANKED);
        assertThat(resultRows.findById(incumbent).orElseThrow().getEndedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRefuseAPreparedOldCapturePieceAndFenceTheNewerOneAfterATitleOnlyEdit(IdentityProviderType type) {
        jdbc.update("""
            INSERT INTO instance_settings(id,silent_mode_engaged,version) VALUES(1,false,0)
            ON CONFLICT(id) DO UPDATE SET silent_mode_engaged=false
            """);
        var prepared = prepare(type, false);
        var item = prepared.item();
        var pull = pulls.findById(Objects.requireNonNull(item.sourceId())).orElseThrow();
        var author =
                users.findById(Objects.requireNonNull(pull.getAuthor()).getId()).orElseThrow();
        var practice = practices
                .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                .orElseThrow();
        practice.setFeedbackMode(FeedbackMode.AUTOMATIC);
        practices.saveAndFlush(practice);
        var scenario = new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(author)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
        long developer = Objects.requireNonNull(scenario.account().getId());
        var old = admissions.pending(
                scenario,
                Instant.now().minusSeconds(120),
                developer,
                prepared.version().value());
        // Captured at the old title too, but admitted only after the newer review.
        var late = admissions.pending(
                scenario,
                Instant.now().minusSeconds(90),
                developer,
                prepared.version().value());
        running(old);
        var oldResult = admit(old);
        assertThat(resultRows.findById(oldResult).orElseThrow().getEndedAt()).isNull();
        var oldPiece = onWork(
                old,
                oldResult,
                publication.issue(
                        item.workspaceId(), item.itemId(), old.review().getId(), List.of(developer)));
        var oldFeedback = feedbackRows.findById(oldPiece).orElseThrow();
        assertThat(oldFeedback.getState())
                .as("Prepared public piece: %s", oldFeedback.getStopReason())
                .isEqualTo(FeedbackState.READY);
        assertThat(sends.findById(oldPiece).orElseThrow().getState()).isEqualTo(FeedbackSendState.PENDING);

        transactions.executeWithoutResult(ignored -> {
            var changed = pulls.findById(pull.getId()).orElseThrow();
            changed.setTitle("A retitled change");
            pulls.saveAndFlush(changed);
            publisher.publishEvent(new ScmDomainEvent.PullRequestUpdated(
                    ScmEventPayload.PullRequestData.from(changed),
                    Set.of("title"),
                    new EventContext(
                            UUID.randomUUID(),
                            Instant.now(),
                            item.workspaceId(),
                            null,
                            DataSource.WEBHOOK,
                            "edited",
                            UUID.randomUUID().toString(),
                            type,
                            author.getId())));
        });
        var canonical = source.pin(item, null).version();
        assertThat(canonical.value()).isNotEqualTo(prepared.version().value());
        assertThat(canonical.baseVersion()).isEqualTo(prepared.version().baseVersion());
        assertThat(ScmVersion.reviewedHead(canonical)).isEqualTo(ScmVersion.reviewedHead(prepared.version()));
        var newer = admissions.pending(scenario, Instant.now().minusSeconds(60), developer, canonical.value());
        running(newer);
        var newerResult = admit(newer);
        assertThat(resultRows.findById(oldResult).orElseThrow().getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
        running(late);
        var lateRow = resultRows.findById(admit(late)).orElseThrow();
        assertThat(lateRow.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(lateRow.getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
        assertThat(resultRows.findById(newerResult).orElseThrow().getEndedAt()).isNull();

        String oldOwner = "owner-" + oldPiece;
        assertThat(delivery.claim(item.workspaceId(), oldPiece, oldOwner)).isNotNull();
        assertThat(delivery.fence(
                        item.workspaceId(),
                        oldPiece,
                        oldOwner,
                        List.of(FeedbackIds.copy(oldPiece, FeedbackCopyKind.SUMMARY, 0)),
                        List.of()))
                .isFalse();
        var refused = feedbackRows.findById(oldPiece).orElseThrow();
        assertThat(refused.getState()).isEqualTo(FeedbackState.WITHHELD);
        assertThat(refused.getStopReason()).isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
        assertThat(copyRows.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(item.workspaceId(), oldPiece))
                .isNotEmpty()
                .allSatisfy(copy -> {
                    assertThat(copy.getState()).isNotEqualTo(FeedbackCopyState.WRITE_STARTED);
                    assertThat(copy.getWriteStartedAt()).isNull();
                });

        // The newer composer reads history only after the old piece was refused.
        var newerPiece = onWork(
                newer,
                newerResult,
                publication.issue(
                        item.workspaceId(), item.itemId(), newer.review().getId(), List.of(developer)));
        assertThat(feedbackRows.findById(newerPiece).orElseThrow().getState()).isEqualTo(FeedbackState.READY);
        String newerOwner = "owner-" + newerPiece;
        var summary = FeedbackIds.copy(newerPiece, FeedbackCopyKind.SUMMARY, 0);
        assertThat(delivery.claim(item.workspaceId(), newerPiece, newerOwner)).isNotNull();
        assertThat(delivery.fence(item.workspaceId(), newerPiece, newerOwner, List.of(summary), List.of()))
                .isTrue();
        assertThat(copyRows.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(item.workspaceId(), newerPiece))
                .singleElement()
                .satisfies(copy -> {
                    assertThat(copy.getId()).isEqualTo(summary);
                    assertThat(copy.getState()).isEqualTo(FeedbackCopyState.WRITE_STARTED);
                });
    }

    private UUID admit(ReviewAdmissionData.Pending pending) {
        return resultStore
                .admit(
                        pending.review().getWorkspaceId(),
                        pending.review().getId(),
                        pending.jobId(),
                        1,
                        List.of(admissions.candidate(pending, ResultOutcome.NOT_MET)))
                .resultIds()
                .getFirst();
    }

    /** One public summary prepared through the real store, retaining the publication context its composer read. */
    private UUID onWork(ReviewAdmissionData.Pending pending, UUID resultId, String context) {
        long workspaceId = pending.review().getWorkspaceId();
        UUID reviewId = pending.review().getId();
        long developer = Objects.requireNonNull(pending.developerId());
        UUID id = FeedbackIds.onWork(workspaceId, reviewId, developer);
        UUID copy = FeedbackIds.copy(id, FeedbackCopyKind.SUMMARY, 0);
        feedbackStore.prepareAll(
                workspaceId,
                reviewId,
                List.of(new FeedbackDraft(
                        id,
                        workspaceId,
                        reviewId,
                        pending.review().getItemId(),
                        developer,
                        FeedbackDestination.ON_WORK,
                        FeedbackState.READY,
                        null,
                        "Check the changed branch.",
                        List.of(new FeedbackBasisInput(resultId, null, 0)),
                        List.of(new FeedbackCopyInput(
                                copy,
                                FeedbackCopyKind.SUMMARY,
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                copy.toString(),
                                FeedbackIds.writeMarker(copy))),
                        Map.of(),
                        new FeedbackCompositionTrace(
                                List.of(new FeedbackCompositionTrace.CopySupport(
                                        FeedbackCopyKind.SUMMARY,
                                        0,
                                        List.of(new FeedbackCompositionTrace.Support(resultId, null)))),
                                List.of(),
                                null,
                                null,
                                context),
                        null)));
        return id;
    }

    @ParameterizedTest
    @CsvSource({"GITHUB,false", "GITHUB,true", "GITLAB,false", "GITLAB,true"})
    void shouldSerializeAdmissionAndNativeSourceChangeOnTheExistingItemLock(
            IdentityProviderType type, boolean admissionFirst) throws Exception {
        var prepared = prepare(type, false);
        var item = prepared.item();
        var pull = pulls.findById(Objects.requireNonNull(item.sourceId())).orElseThrow();
        var author =
                users.findById(Objects.requireNonNull(pull.getAuthor()).getId()).orElseThrow();
        var scenario = new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(author)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
        var pending = admissions.pending(
                scenario,
                Instant.now().minusSeconds(30),
                scenario.account().getId(),
                prepared.version().value());
        running(pending);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var changed = new CountDownLatch(1);
        var admissionStarted = new CountDownLatch(1);
        var executor =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            if (admissionFirst) {
                doAnswer(call -> {
                            Object canonical = call.callRealMethod();
                            held.countDown();
                            awaitLatch(release);
                            return canonical;
                        })
                        .when(productItems)
                        .current(item);
            }
            Future<?> sourceWrite;
            Future<AdmissionReceipt> admitted;
            if (admissionFirst) {
                admitted = executor.submit(() -> resultStore.admit(
                        item.workspaceId(),
                        pending.review().getId(),
                        pending.jobId(),
                        1,
                        List.of(admissions.candidate(pending, ResultOutcome.MET))));
                awaitLatch(held);
                sourceWrite = executor.submit(() -> changeNativeBody(prepared, type, false, held, release, changed));
                awaitLatch(changed);
            } else {
                sourceWrite = executor.submit(() -> changeNativeBody(prepared, type, true, held, release, changed));
                awaitLatch(held);
                admitted = executor.submit(() -> {
                    admissionStarted.countDown();
                    return resultStore.admit(
                            item.workspaceId(),
                            pending.review().getId(),
                            pending.jobId(),
                            1,
                            List.of(admissions.candidate(pending, ResultOutcome.MET)));
                });
                awaitLatch(admissionStarted);
            }
            release.countDown();
            var receipt = admitted.get(20, TimeUnit.SECONDS);
            sourceWrite.get(20, TimeUnit.SECONDS);

            assertThat(source.pin(item, null).version()).isNotEqualTo(prepared.version());
            var historical = resultRows.findById(receipt.resultIds().getFirst()).orElseThrow();
            assertThat(historical.getItemVersion()).isEqualTo(prepared.version().value());
            assertThat(historical.getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
            assertThat(resultRows.forDevelopersSince(
                            item.workspaceId(), List.of(scenario.account().getId()), Instant.EPOCH, true))
                    .extracting(row -> row.getId())
                    .doesNotContain(historical.getId());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldKeepTheSubmittedReviewCurrentWhenOnlyTheAuthorPushes(IdentityProviderType type) {
        var prepared = prepare(type, true);
        var item = prepared.item();
        var scenario = reviewScenario(prepared);
        var nativeReview = providerReviews
                .findById(Objects.requireNonNull(item.sourceId()))
                .orElseThrow();
        long pullId = Objects.requireNonNull(nativeReview.getPullRequest()).getId();
        var submittedCommit = nativeReview.getCommitId();
        var pending = admissions.pending(
                scenario,
                Instant.now().minusSeconds(30),
                scenario.account().getId(),
                prepared.version().value());
        running(pending);
        transactions.executeWithoutResult(ignored -> {
            var changed = pulls.findById(pullId).orElseThrow();
            changed.setHeadRefOid("c".repeat(40));
            pulls.saveAndFlush(changed);
            var context = new EventContext(
                    UUID.randomUUID(),
                    Instant.now(),
                    item.workspaceId(),
                    null,
                    DataSource.WEBHOOK,
                    "synchronize",
                    UUID.randomUUID().toString(),
                    type,
                    Objects.requireNonNull(changed.getAuthor()).getId());
            publisher.publishEvent(
                    new ScmDomainEvent.PullRequestSynchronized(ScmEventPayload.PullRequestData.from(changed), context));
        });
        assertThat(source.pin(item, null).version()).isNotEqualTo(prepared.version());
        var unchanged = providerReviews.findById(nativeReview.getId()).orElseThrow();
        assertThat(unchanged.getCommitId()).isEqualTo(submittedCommit);
        assertThat(unchanged.getBody()).isEqualTo(nativeReview.getBody());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM item_event WHERE workspace_id=? AND item_id=?",
                        Long.class,
                        item.workspaceId(),
                        item.itemId()))
                .isZero();

        var receipt = resultStore.admit(
                item.workspaceId(),
                pending.review().getId(),
                pending.jobId(),
                1,
                List.of(admissions.candidate(pending, ResultOutcome.MET)));

        var result = resultRows.findById(receipt.resultIds().getFirst()).orElseThrow();
        assertThat(result.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(result.getEndedAt()).isNull();
        assertThat(resultRows.forDevelopersSince(
                        item.workspaceId(), List.of(scenario.account().getId()), Instant.EPOCH, true))
                .extracting(row -> row.getId())
                .contains(result.getId());
    }

    @ParameterizedTest
    @EnumSource(
            value = IdentityProviderType.class,
            names = {"GITHUB", "GITLAB"})
    void shouldRetireThePriorClaimWhenTheSubmittedReviewItselfChanges(IdentityProviderType type) {
        var prepared = prepare(type, true);
        var item = prepared.item();
        var scenario = reviewScenario(prepared);
        var at = Instant.now().minusSeconds(30);
        var current = resultData.result(
                scenario, ResultOutcome.MET, at, at, prepared.version().value());
        transactions.executeWithoutResult(ignored -> {
            var changed = providerReviews
                    .findById(Objects.requireNonNull(item.sourceId()))
                    .orElseThrow();
            changed.setBody("A revised submitted review.");
            providerReviews.saveAndFlush(changed);
            var context = new EventContext(
                    UUID.randomUUID(),
                    Instant.now(),
                    item.workspaceId(),
                    null,
                    DataSource.WEBHOOK,
                    "submitted",
                    UUID.randomUUID().toString(),
                    type,
                    Objects.requireNonNull(changed.getAuthor()).getId());
            publisher.publishEvent(new ScmDomainEvent.ReviewSubmitted(
                    ScmEventPayload.ReviewData.from(changed).orElseThrow(), context));
        });
        assertThat(source.pin(item, null).version()).isNotEqualTo(prepared.version());
        assertThat(resultRows.findById(current.getId()).orElseThrow().getEndReason())
                .isEqualTo(ResultEndReason.WORK_CHANGED);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM item_event WHERE workspace_id=? AND item_id=?",
                        Long.class,
                        item.workspaceId(),
                        item.itemId()))
                .isEqualTo(1L);
    }

    private ReviewIntegrationData.Scenario reviewScenario(Prepared prepared) {
        var item = prepared.item();
        var review = providerReviews
                .findById(Objects.requireNonNull(item.sourceId()))
                .orElseThrow();
        var reviewer = users.findById(Objects.requireNonNull(review.getAuthor()).getId())
                .orElseThrow();
        return new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(reviewer)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
    }

    private void changeNativeBody(
            Prepared prepared,
            IdentityProviderType type,
            boolean holdSource,
            CountDownLatch held,
            CountDownLatch release,
            CountDownLatch changed) {
        transactions.executeWithoutResult(ignored -> {
            var item = prepared.item();
            if (holdSource) writer.lock(item.workspaceId(), item.itemId());
            var work = pulls.findById(Objects.requireNonNull(item.sourceId())).orElseThrow();
            work.setBody("A concurrent native material edit.");
            pulls.saveAndFlush(work);
            var context = new EventContext(
                    UUID.randomUUID(),
                    Instant.now(),
                    item.workspaceId(),
                    null,
                    DataSource.WEBHOOK,
                    "edited",
                    UUID.randomUUID().toString(),
                    type,
                    Objects.requireNonNull(work.getAuthor()).getId());
            publisher.publishEvent(new ScmDomainEvent.PullRequestUpdated(
                    ScmEventPayload.PullRequestData.from(work), Set.of("body"), context));
            changed.countDown();
            if (holdSource) {
                held.countDown();
                awaitLatch(release);
            }
        });
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent fixture interrupted", interrupted);
        }
    }

    private void running(ReviewAdmissionData.Pending pending) {
        admissions.running(pending, 1);
        when(custody.find(anyLong(), anyList()))
                .thenAnswer(call -> new HistoryCustody(true, false, List.of(), List.of(), call.getArgument(1)));
        when(runtime.activeJobIds(
                        pending.review().getWorkspaceId(), pending.review().getId()))
                .thenReturn(Set.of(pending.jobId()));
        when(runtime.unsettledJobIds(
                        pending.review().getWorkspaceId(), pending.review().getId()))
                .thenReturn(Set.of());
        when(runtime.runtime(pending.review().getWorkspaceId(), pending.review().getId()))
                .thenReturn(Optional.of(new ReviewRuntimeSnapshot(List.of(new ReviewJobSnapshot(
                        pending.jobId(),
                        ReviewJobStatus.RUNNING,
                        1,
                        ProcessingStatus.PENDING,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        "late-capture")))));
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
    void shouldRetireCurrentClaimsWhenReadinessPinsToAnExistingOccasion() {
        var prepared = prepare(IdentityProviderType.GITLAB, false);
        var item = prepared.item();
        long pullId = Objects.requireNonNull(item.sourceId());
        var pull = pulls.findById(pullId).orElseThrow();
        var author =
                users.findById(Objects.requireNonNull(pull.getAuthor()).getId()).orElseThrow();
        var scenario = new ReviewIntegrationData.Scenario(
                workspaces.findById(item.workspaceId()).orElseThrow(),
                accounts.findById(account(author)).orElseThrow(),
                practices
                        .findByWorkspaceIdAndSlug(item.workspaceId(), "event-test")
                        .orElseThrow(),
                itemRows.findByIdAndWorkspaceId(item.itemId(), item.workspaceId())
                        .orElseThrow());
        synchronizeHead(prepared, "b".repeat(40), false);
        var original = events.findByWorkspaceIdAndItemIdAndNameAndItemVersion(
                        item.workspaceId(),
                        item.itemId(),
                        ScmItemEvents.PULL_REQUEST_SYNCHRONIZED.value(),
                        prepared.version().value())
                .orElseThrow();
        synchronizeHead(prepared, "c".repeat(40), false);
        var intervening = source.pin(item, null).version();
        assertThat(intervening).isNotEqualTo(prepared.version());
        var at = Instant.now();
        var current = resultData.result(scenario, ResultOutcome.MET, at, at, intervening.value());

        // GitLab stores a force-pushed head before its readiness read pairs that head's diff base.
        synchronizeHead(prepared, "b".repeat(40), true);
        var pendingIds = jdbc.queryForList(
                "SELECT id FROM item_event WHERE workspace_id=? AND item_id=?" + " AND name=? AND item_version IS NULL",
                UUID.class,
                item.workspaceId(),
                item.itemId(),
                ScmItemEvents.PULL_REQUEST_SYNCHRONIZED.value());
        assertThat(pendingIds).hasSize(1);
        var pending = events.findById(pendingIds.getFirst()).orElseThrow();
        assertThat(pending.getState()).isEqualTo(ItemEventState.RECORDED);
        assertThat(resultRows.findById(current.getId()).orElseThrow().getEndedAt())
                .isNull();
        assertThat(resultRows.forDevelopersSince(
                        item.workspaceId(), List.of(scenario.account().getId()), Instant.EPOCH, true))
                .extracting(row -> row.getId())
                .contains(current.getId());
        var occasions = jdbc.queryForList(
                "SELECT id FROM item_event WHERE workspace_id=? AND item_id=?",
                UUID.class,
                item.workspaceId(),
                item.itemId());
        var reviewIds = itemReviews(prepared).stream().map(Review::getId).toList();
        long recorded = applicationEvents.stream(ItemEventRecorded.class)
                .filter(event -> event.workspaceId() == item.workspaceId() && event.itemId() == item.itemId())
                .count();
        assertThat(applicationEvents.stream(ItemSourceObserved.class)
                        .filter(event -> event.workspaceId() == item.workspaceId() && event.itemId() == item.itemId()))
                .isEmpty();

        assertThat(readiness(
                        pulls.findById(pullId).orElseThrow(),
                        new GitLabMergeRequestReadinessReader.DiffRefs("b".repeat(40), "a".repeat(40))))
                .isTrue();

        var coalesced = events.findById(pending.getId()).orElseThrow();
        assertThat(coalesced.getState()).isEqualTo(ItemEventState.WITHHELD);
        assertThat(coalesced.getReason()).isEqualTo(ReviewRequestReason.COALESCED);
        assertThat(coalesced.getItemVersion()).isNull();
        assertThat(coalesced.getOccurredAt()).isEqualTo(pending.getOccurredAt());
        var retained = events.findById(original.getId()).orElseThrow();
        assertThat(retained.getItemVersion()).isEqualTo(prepared.version().value());
        assertThat(retained.getOccurredAt()).isEqualTo(original.getOccurredAt());
        assertThat(retained.getState()).isEqualTo(original.getState());
        assertThat(jdbc.queryForList(
                        "SELECT id FROM item_event WHERE workspace_id=? AND item_id=?",
                        UUID.class,
                        item.workspaceId(),
                        item.itemId()))
                .containsExactlyInAnyOrderElementsOf(occasions);
        assertThat(itemReviews(prepared)).extracting(Review::getId).containsExactlyInAnyOrderElementsOf(reviewIds);
        assertThat(applicationEvents.stream(ItemEventRecorded.class)
                        .filter(event -> event.workspaceId() == item.workspaceId() && event.itemId() == item.itemId())
                        .count())
                .isEqualTo(recorded);
        assertThat(source.pin(item, null).version()).isEqualTo(prepared.version());
        var retired = resultRows.findById(current.getId()).orElseThrow();
        assertThat(retired.getItemVersion()).isEqualTo(intervening.value());
        assertThat(retired.getEndReason()).isEqualTo(ResultEndReason.WORK_CHANGED);
        assertThat(resultRows.forDevelopersSince(
                        item.workspaceId(), List.of(scenario.account().getId()), Instant.EPOCH, true))
                .extracting(row -> row.getId())
                .doesNotContain(current.getId());
        assertThat(applicationEvents.stream(ItemSourceObserved.class)
                        .filter(event -> event.workspaceId() == item.workspaceId() && event.itemId() == item.itemId()))
                .hasSize(1);
    }

    private void synchronizeHead(Prepared prepared, String head, boolean withoutBase) {
        transactions.executeWithoutResult(ignored -> {
            long pullId = Objects.requireNonNull(prepared.item().sourceId());
            if (withoutBase) jdbc.update("UPDATE issue SET base_ref_oid=NULL WHERE id=?", pullId);
            var pull = pulls.findById(pullId).orElseThrow();
            pull.setHeadRefOid(head);
            pulls.saveAndFlush(pull);
            var context = new EventContext(
                    UUID.randomUUID(),
                    Instant.now(),
                    prepared.item().workspaceId(),
                    null,
                    DataSource.WEBHOOK,
                    "synchronize",
                    UUID.randomUUID().toString(),
                    IdentityProviderType.GITLAB,
                    Objects.requireNonNull(pull.getAuthor()).getId());
            publisher.publishEvent(
                    new ScmDomainEvent.PullRequestSynchronized(ScmEventPayload.PullRequestData.from(pull), context));
        });
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
