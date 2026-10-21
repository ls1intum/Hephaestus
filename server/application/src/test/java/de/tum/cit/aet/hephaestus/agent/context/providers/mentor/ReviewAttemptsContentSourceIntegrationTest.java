package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.review.ReviewItemRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retained review runs read for Heph's context from real review rows. Each run is created as it was retained, never
 * repaired or edited afterwards: its practice was pinned to a version but no executable method, so execution refuses
 * it while its review row still says whose work it was. Rows are seeded directly; no result, evidence or delivery is
 * created, and no review service ran to produce them.
 */
@Import(ReviewIntegrationData.class)
class ReviewAttemptsContentSourceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ReviewAttemptsContentSource attempts;

    @Autowired
    ReviewAssignmentQuery assignments;

    @Autowired
    ReviewJobAssignments execution;

    @Autowired
    ReviewJobRepository jobs;

    @Autowired
    ReviewRepository reviews;

    @Autowired
    ReviewPracticeRepository practices;

    @Autowired
    ReviewItemRepository items;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    ConnectionRepository connections;

    @Autowired
    IdentityProviderRepository providers;

    @Autowired
    RepositoryRepository repositories;

    @Autowired
    RepositoryToMonitorRepository monitors;

    @Autowired
    PullRequestRepository pulls;

    @Autowired
    ItemIndexService itemIndex;

    @Autowired
    ItemRepository productItems;

    Scenario scenario;
    long developerId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        wireScmWork();
        developerId = Objects.requireNonNull(scenario.account().getId());
    }

    /** A real monitored source and its canonical item lineage, so current mentoring permission is exercised. */
    private void wireScmWork() {
        var workspace = scenario.workspace();
        long nativeId = Objects.requireNonNull(scenario.item().getSourceId());
        var connection = new Connection(
                workspace,
                IntegrationKind.GITHUB,
                Long.toString(workspace.getId()),
                new ConnectionConfig.GitHubAppConfig(workspace.getId(), workspace.getAccountLogin(), null, Set.of()));
        connection.setState(IntegrationState.ACTIVE);
        connections.saveAndFlush(connection);
        var provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> providers.saveAndFlush(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        var repository = new Repository();
        repository.setNativeId(nativeId);
        repository.setProvider(provider);
        repository.setName("retained-work");
        repository.setNameWithOwner(workspace.getAccountLogin() + "/retained-work");
        repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        repository.setDefaultBranch("main");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repositories.saveAndFlush(repository);
        var monitor = WorkspaceTestFixtures.repositoryMonitor(workspace, repository.getNameWithOwner());
        monitor.setNativeId(repository.getNativeId());
        monitors.saveAndFlush(monitor);
        var pull = new PullRequest();
        pull.setNativeId(nativeId);
        pull.setProvider(provider);
        pull.setRepository(repository);
        pull.setNumber(1);
        pull.setTitle("Retained reviewed work");
        pull.setState(Issue.State.OPEN);
        pull.setHtmlUrl(repository.getHtmlUrl() + "/pull/1");
        pull.setCreatedAt(Instant.now());
        pull.setUpdatedAt(Instant.now());
        pulls.saveAndFlush(pull);
        var parent = itemIndex.upsert(
                new ItemRegistration(workspace(), ScmItemEvents.REPOSITORY, repository.getId(), null, null));
        var work = itemIndex.upsert(
                new ItemRegistration(workspace(), ScmItemEvents.PULL_REQUEST, pull.getId(), parent.itemId(), null));
        scenario = new Scenario(
                workspace,
                scenario.account(),
                scenario.practice(),
                productItems.findByIdAndWorkspaceId(work.itemId(), workspace()).orElseThrow());
    }

    private long workspace() {
        return scenario.workspace().getId();
    }

    /**
     * A retained ITEM review about the scenario's developer whose practice has no executable method. With
     * {@code boundJob}, a retained run that recorded no assignments is created first and the review's row is bound to
     * it, as the review binds the run it hands an assignment to.
     */
    private ReviewAssignmentId retainedReview(@Nullable UUID boundJob) {
        return retainedReview(boundJob, json.createObjectNode().put("developerId", developerId));
    }

    /** As {@link #retainedReview(UUID)}, with the row's attribution detail as it was retained. */
    private ReviewAssignmentId retainedReview(@Nullable UUID boundJob, JsonNode detail) {
        var now = Instant.now();
        var review = reviews.saveAndFlush(Review.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace())
                .itemId(scenario.item().getId())
                .kind(ReviewKind.ITEM)
                .origin(ReviewOrigin.REQUEST)
                .requestedById(developerId)
                .status(ReviewStatus.PARTIAL)
                .stopReason(ReviewStopReason.LEGACY_SCOPE_UNKNOWN)
                .itemVersion("retained-work-version")
                .orderedAt(now)
                .createdAt(now)
                .admittedAt(now)
                .completedAt(now)
                .build());
        practices.saveAndFlush(ReviewPractice.builder()
                .workspaceId(workspace())
                .reviewId(review.getId())
                .practiceId(scenario.practice().getId())
                .practiceVersionId(scenario.practice().getCurrentVersion().getId())
                .build());
        if (boundJob != null) retainedRun(boundJob, review.getId(), List.of());
        items.saveAndFlush(ReviewItem.builder()
                .workspaceId(workspace())
                .reviewId(review.getId())
                .practiceId(scenario.practice().getId())
                .itemId(scenario.item().getId())
                .itemVersion(review.getItemVersion())
                .jobId(boundJob)
                .detail(detail)
                .build());
        return new ReviewAssignmentId(
                review.getId(), scenario.practice().getId(), scenario.item().getId());
    }

    /** A retained failed run of the review, recording {@code recorded} as its frozen assignments. */
    private UUID retainedRun(UUID jobId, UUID reviewId, List<ReviewAssignmentId> recorded) {
        jdbc.sql("""
            INSERT INTO review_job(id,review_id,workspace_id,status,attempt,processing_status,trace_id,input,config_snapshot,
                submitted_at,available_at,started_at,completed_at)
            VALUES(:job,:review,:ws,'FAILED',1,'DONE',:trace,CAST(:input AS jsonb),'{}'::jsonb,now(),now(),now(),now())
            """)
                .param("job", jobId)
                .param("review", reviewId)
                .param("ws", workspace())
                .param("trace", jobId.toString().replace("-", ""))
                .param(
                        "input",
                        json.writeValueAsString(json.createObjectNode()
                                .put("itemId", scenario.item().getId())
                                .set("assignments", json.valueToTree(recorded))))
                .update();
        return jobId;
    }

    @Test
    void shouldReadWhoARetainedRunWasAboutWhileExecutionStillRefusesIt() {
        var assignment = retainedReview(null);
        var jobId = retainedRun(UUID.randomUUID(), assignment.reviewId(), List.of(assignment));

        var job = jobs.findById(jobId).orElseThrow();
        assertThatThrownBy(() -> execution.load(job))
                .as("the execution owner refuses an assignment without an executable method, as it must")
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("frozen review assignment is unavailable");

        assertThat(assignments.retained(workspace(), assignment.reviewId(), jobId, List.of(assignment)))
                .singleElement()
                .satisfies(retained -> {
                    assertThat(retained.id()).isEqualTo(assignment);
                    assertThat(retained.item().itemId())
                            .isEqualTo(scenario.item().getId());
                    assertThat(retained.developerId()).isEqualTo(developerId);
                });
        var overview = attempts.overview(workspace(), developerId);
        assertThat(overview.path("attempts"))
                .singleElement()
                .satisfies(row -> assertThat(row.path("jobId").asString()).isEqualTo(jobId.toString()));
        assertThat(overview.path("coverage").path("attributionComplete").asBoolean())
                .as("the retained run is attributable, so coverage is complete and the context does not fail")
                .isTrue();
    }

    @Test
    void shouldReadRetainedAttributionOnlyInItsOwnWorkspaceReviewAndForItsOwnDeveloper() {
        var assignment = retainedReview(null);
        var jobId = retainedRun(UUID.randomUUID(), assignment.reviewId(), List.of(assignment));
        var other = data.scenario();

        assertThat(assignments.retained(other.workspace().getId(), assignment.reviewId(), jobId, List.of(assignment)))
                .as("another workspace cannot read this review's attribution")
                .isEmpty();
        assertThat(assignments.retained(
                        workspace(),
                        assignment.reviewId(),
                        jobId,
                        List.of(new ReviewAssignmentId(
                                UUID.randomUUID(), assignment.practiceId(), assignment.itemId()))))
                .as("a key naming another review selects nothing")
                .isEmpty();
        var colleagueId = Objects.requireNonNull(data.member(workspace()).getId());
        assertThat(attempts.overview(workspace(), colleagueId).toString())
                .as("a run about one developer is never shown to another")
                .doesNotContain(jobId.toString());
    }

    @Test
    void shouldReadARunWithoutRecordedAssignmentsOnlyThroughTheReviewsBindingToIt() {
        var boundJob = UUID.randomUUID();
        var bound = retainedReview(boundJob);
        var unbound = retainedReview(null);
        var unboundJob = retainedRun(UUID.randomUUID(), unbound.reviewId(), List.of());

        assertThat(assignments.retained(workspace(), bound.reviewId(), boundJob, List.of()))
                .singleElement()
                .satisfies(retained -> assertThat(retained.developerId()).isEqualTo(developerId));
        assertThat(assignments.retained(workspace(), unbound.reviewId(), unboundJob, List.of()))
                .as("nothing binds the review's rows to this run, so its attribution is unknown")
                .isEmpty();
        assertThat(attempts.overview(workspace(), developerId)
                        .path("coverage")
                        .path("attributionComplete")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void shouldReadExactlyTheRecordedRowsOfTheRunsOwnReviewAndRefuseAnyKeyItCannotRead() {
        var first = retainedReview(null);
        var second = retainedReview(null);
        var jobId = retainedRun(UUID.randomUUID(), first.reviewId(), List.of(first));

        assertThat(assignments.retained(workspace(), first.reviewId(), jobId, List.of(first)))
                .as("one scoped read returns this review's recorded row, not the same item's row of another review")
                .extracting(ReviewAssignmentQuery.Retained::id)
                .containsExactly(first);
        assertThat(assignments.retained(workspace(), first.reviewId(), jobId, List.of(first, second)))
                .as("a key of another review refuses the whole selection")
                .isEmpty();
        assertThat(assignments.retained(
                        workspace(),
                        first.reviewId(),
                        jobId,
                        List.of(
                                first,
                                new ReviewAssignmentId(
                                        first.reviewId(), first.practiceId() + 1_000_000, first.itemId()))))
                .as("a recorded key without its retained row refuses the whole selection")
                .isEmpty();
    }

    @Test
    void shouldAttributeABoundRunOnlyWhenItsBoundRowSaysWhoseWorkItWas() {
        var readableJob = UUID.randomUUID();
        var readable = retainedReview(readableJob);
        var unreadableJob = UUID.randomUUID();
        var unreadable = retainedReview(unreadableJob, json.createObjectNode());

        assertThat(assignments.retained(workspace(), readable.reviewId(), readableJob, List.of()))
                .singleElement()
                .satisfies(retained -> assertThat(retained.developerId()).isEqualTo(developerId));
        assertThat(assignments.retained(workspace(), unreadable.reviewId(), unreadableJob, List.of()))
                .as("a bound row without a developer leaves its run unattributed rather than half read")
                .isEmpty();
        assertThat(assignments.retained(workspace(), unreadable.reviewId(), readableJob, List.of()))
                .as("another review's binding does not attribute this review")
                .isEmpty();
    }
}
