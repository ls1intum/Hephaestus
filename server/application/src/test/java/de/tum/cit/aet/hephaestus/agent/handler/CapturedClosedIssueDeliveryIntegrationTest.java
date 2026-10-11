package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.documentation.DocumentProjection;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.IssueObservationSuperseder;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMemberAiPolicy;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSubjectStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkChanges;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewPersonMode;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewRepositoryMode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A review of an issue captured closed reaches that issue while it stays closed under the reviewed snapshot. The
 * snapshot is the one the mirror's own owner records: each change goes through {@link IssueObservationSuperseder} and
 * the issue row's compare-and-set, so reopening, closing again, editing and undoing the edit each leave the old review
 * behind. The settings, coverage and consent facts the snapshot does not decide are fixed to pass.
 */
class CapturedClosedIssueDeliveryIntegrationTest extends BaseIntegrationTest {

    private static final String TITLE = "Export the reading list";
    private static final Instant CLOSED_AT = Instant.parse("2026-10-01T10:00:00Z");
    private static final Set<String> PRACTICES = Set.of("closure-record");

    @Autowired
    private IssueObservationSuperseder superseder;

    @Autowired
    private IssueRepository issues;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private UserRepository users;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    private final PracticeReviewCoverageService coverage = mock(PracticeReviewCoverageService.class);
    private final AccountPreferencesQuery preferences = mock(AccountPreferencesQuery.class);
    private final ReviewMemberAiPolicy memberAi = mock(ReviewMemberAiPolicy.class);
    private final PracticeRepository practices = mock(PracticeRepository.class);

    private PracticeFeedbackDeliveryPolicy policy;
    private Workspace workspace;
    private Repository repository;
    private long issueId;
    private long unrelatedId;
    private UUID unrelatedSnapshot;
    private String unrelatedDigest;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        long nativeId = ThreadLocalRandom.current().nextLong(1000, Long.MAX_VALUE - 3);
        Workspace unsaved = WorkspaceTestFixtures.activeWorkspace("closed-" + suffix);
        unsaved.getFeatures().setPracticesEnabled(true);
        unsaved.getReviewSettings().setDeliveryStatus(PracticeDeliveryStatus.ACTIVE);
        workspace = workspaces.save(unsaved);

        IdentityProvider provider = providers
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(
                        () -> providers.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        User author = users.save(TestUserFactory.createUser(nativeId, "author-" + suffix, provider));
        Repository repo = new Repository();
        repo.setNativeId(nativeId + 1);
        repo.setProvider(provider);
        repo.setName("project-" + suffix);
        repo.setNameWithOwner("org/project-" + suffix);
        repo.setHtmlUrl("https://github.com/" + repo.getNameWithOwner());
        repo.setDefaultBranch("main");
        repository = repositories.save(repo);
        monitors.save(WorkspaceTestFixtures.repositoryMonitor(workspace, repository.getNameWithOwner()));

        issueId = saveClosedIssue(provider, author, 7, nativeId + 2);
        unrelatedId = saveClosedIssue(provider, author, 8, nativeId + 3);
        closeAndAdvance(issueId, CLOSED_AT);
        closeAndAdvance(unrelatedId, CLOSED_AT);
        unrelatedSnapshot = snapshot(unrelatedId);
        unrelatedDigest = digest(unrelatedId);

        when(coverage.assess(any(), eq(repository.getNameWithOwner()), eq(null), any(), eq(false)))
                .thenReturn(new PracticeReviewCoverageService.CoverageAssessment(
                        ReviewRepositoryMode.ALL_MONITORED,
                        ReviewPersonMode.ALL_ELIGIBLE,
                        ReviewSubjectStatus.RESOLVED_LINKED_HUMAN,
                        true,
                        true,
                        true,
                        true));
        when(preferences.practiceFeedbackDeliveryEnabled(anyLong())).thenReturn(true);
        when(memberAi.allowsResult(any())).thenReturn(true);
        Practice practice = new Practice();
        practice.setSlug("closure-record");
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        when(practices.findByWorkspaceIdAndSlugIn(workspace.getId(), PRACTICES)).thenReturn(List.of(practice));
        policy = new PracticeFeedbackDeliveryPolicy(
                issues,
                mock(PullRequestRepository.class),
                mock(ReviewTargetQuery.class),
                monitors,
                workspaces,
                preferences,
                mock(PracticeReviewProperties.class),
                mock(SilentModeQuery.class),
                coverage,
                mock(DeliveryPolicyEvaluationRecorder.class),
                practices,
                mock(FeedbackApprovalRepository.class),
                mock(ConversationSourceLiveness.class),
                memberAi,
                mock(DocumentProjection.class),
                mock(PublicReviewEligibility.class),
                mock(ReviewedWorkChanges.class),
                List.of(),
                mock(EntityManager.class));
    }

    @Test
    void shouldDeliverToAnUnchangedClosedIssueAndRefuseOnceItReopensOrClosesAgain() {
        UUID reviewed = snapshot(issueId);
        AgentJob job = closedReview(reviewed, ScmSignals.ISSUE_CLOSED.value());

        assertThat(evaluate(job).allowed()).isTrue();

        updateAndAdvance(
                issue -> {
                    issue.setState(Issue.State.OPEN);
                    issue.setStateReason(null);
                    issue.setClosedAt(null);
                },
                "state");
        assertThat(snapshot(issueId)).isNotEqualTo(reviewed);
        assertThat(evaluate(job).refusal()).isEqualTo(FeedbackSuppressionReason.ISSUE_SNAPSHOT_CHANGED);

        closeAndAdvance(issueId, CLOSED_AT.plusSeconds(7200));
        assertThat(snapshot(issueId)).isNotEqualTo(reviewed);
        assertThat(evaluate(job).refusal()).isEqualTo(FeedbackSuppressionReason.ISSUE_SNAPSHOT_CHANGED);
        assertUnrelatedUnchanged();
    }

    @Test
    void shouldRefuseTheOldReviewOnceTheClosedIssueIsEditedEvenIfTheEditIsUndone() {
        UUID reviewed = snapshot(issueId);
        AgentJob job = closedReview(reviewed, ScmSignals.ISSUE_UPDATED.value());
        assertThat(evaluate(job).allowed()).isTrue();

        updateAndAdvance(issue -> issue.setTitle(TITLE + " as CSV"), "title");
        UUID edited = snapshot(issueId);
        assertThat(evaluate(job).refusal()).isEqualTo(FeedbackSuppressionReason.ISSUE_SNAPSHOT_CHANGED);

        updateAndAdvance(issue -> issue.setTitle(TITLE), "title");
        UUID restored = snapshot(issueId);
        assertThat(List.of(reviewed, edited, restored)).doesNotHaveDuplicates();
        assertThat(evaluate(job).refusal()).isEqualTo(FeedbackSuppressionReason.ISSUE_SNAPSHOT_CHANGED);
        assertThat(evaluate(closedReview(restored, ScmSignals.ISSUE_UPDATED.value()))
                        .allowed())
                .isTrue();
        assertUnrelatedUnchanged();
    }

    private long saveClosedIssue(IdentityProvider provider, User author, int number, long nativeId) {
        Issue issue = new Issue();
        issue.setNativeId(nativeId);
        issue.setProvider(provider);
        issue.setNumber(number);
        issue.setTitle(TITLE);
        issue.setBody("- [x] CSV export\n- [x] JSON export");
        issue.setState(Issue.State.CLOSED);
        issue.setStateReason(Issue.StateReason.COMPLETED);
        issue.setClosedAt(CLOSED_AT);
        issue.setHtmlUrl(repository.getHtmlUrl() + "/issues/" + number);
        issue.setRepository(repository);
        issue.setAuthor(author);
        issue.setCreatedAt(CLOSED_AT.minusSeconds(3600));
        issue.setUpdatedAt(CLOSED_AT);
        return issues.save(issue).getId();
    }

    /** Closes the mirrored issue, then records its snapshot through the superseder, as a close event does. */
    private void closeAndAdvance(long id, Instant closedAt) {
        transactions.executeWithoutResult(status -> {
            Issue issue = issues.findById(id).orElseThrow();
            issue.setState(Issue.State.CLOSED);
            issue.setStateReason(Issue.StateReason.COMPLETED);
            issue.setClosedAt(closedAt);
            issues.saveAndFlush(issue);
            superseder.onClosed(
                    new ScmDomainEvent.IssueClosed(ScmEventPayload.IssueData.from(issue), "completed", webhook()));
        });
    }

    /** Changes the reviewed issue, then records its snapshot through the superseder, as an update event does. */
    private void updateAndAdvance(Consumer<Issue> change, String field) {
        transactions.executeWithoutResult(status -> {
            Issue issue = issues.findById(issueId).orElseThrow();
            change.accept(issue);
            issues.saveAndFlush(issue);
            superseder.onUpdated(
                    new ScmDomainEvent.IssueUpdated(ScmEventPayload.IssueData.from(issue), Set.of(field), webhook()));
        });
    }

    /** The author's review of the issue as it was captured: closed, under {@code reviewed}. */
    private AgentJob closedReview(UUID reviewed, String signal) {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.put("issue_id", issueId);
        metadata.put("issue_number", 7);
        metadata.put("repository_id", repository.getId());
        metadata.put("repository_full_name", repository.getNameWithOwner());
        metadata.put("state", "CLOSED");
        metadata.put("review_snapshot_id", reviewed.toString());
        metadata.put(PracticeCatalogInjector.SIGNAL_METADATA_KEY, signal);
        AgentJob job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.ISSUE_REVIEW);
        job.setArtifactKind(ArtifactKinds.ISSUE);
        job.setPracticeRolloutRevision(workspace.getReviewSettings().getRolloutRevision());
        job.setMetadata(metadata);
        return job;
    }

    private PracticeFeedbackDeliveryPolicy.Decision<Issue> evaluate(AgentJob job) {
        return Objects.requireNonNull(
                transactions.execute(status -> policy.evaluateIssue(job, DeliveryPolicyStage.EGRESS, null, PRACTICES)));
    }

    private void assertUnrelatedUnchanged() {
        assertThat(snapshot(unrelatedId)).isEqualTo(unrelatedSnapshot);
        assertThat(digest(unrelatedId)).isEqualTo(unrelatedDigest);
    }

    private UUID snapshot(long id) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT review_snapshot_id FROM issue WHERE id = ?", UUID.class, id));
    }

    private String digest(long id) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT review_snapshot_digest FROM issue WHERE id = ?", String.class, id));
    }

    private EventContext webhook() {
        return new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                workspace.getId(),
                new RepositoryRef(repository.getId(), repository.getNameWithOwner(), "main"),
                DataSource.WEBHOOK,
                "closed",
                UUID.randomUUID().toString(),
                null);
    }
}
