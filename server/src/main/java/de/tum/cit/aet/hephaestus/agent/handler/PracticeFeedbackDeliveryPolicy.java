package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationCommand;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRecorder;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyFactsSnapshot;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyResolver;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService.CoverageAssessment;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PracticeFeedbackDeliveryPolicy {

    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountPreferencesQuery accountPreferencesQuery;
    private final PracticeReviewProperties reviewProperties;
    private final SilentModeQuery silentModeQuery;
    private final PracticeReviewCoverageService coverageService;
    private final DeliveryPolicyEvaluationRecorder evaluationRecorder;
    private final PracticeRepository practiceRepository;

    PracticeFeedbackDeliveryPolicy(
        IssueRepository issueRepository,
        PullRequestRepository pullRequestRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepository workspaceRepository,
        AccountPreferencesQuery accountPreferencesQuery,
        PracticeReviewProperties reviewProperties,
        SilentModeQuery silentModeQuery,
        PracticeReviewCoverageService coverageService,
        DeliveryPolicyEvaluationRecorder evaluationRecorder,
        PracticeRepository practiceRepository
    ) {
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceRepository = workspaceRepository;
        this.accountPreferencesQuery = accountPreferencesQuery;
        this.reviewProperties = reviewProperties;
        this.silentModeQuery = silentModeQuery;
        this.coverageService = coverageService;
        this.evaluationRecorder = evaluationRecorder;
        this.practiceRepository = practiceRepository;
    }

    @Transactional(readOnly = true)
    public Decision<Issue> evaluateIssue(AgentJob job) {
        return evaluateIssue(job, DeliveryPolicyStage.AUTOMATIC, null);
    }

    @Transactional(readOnly = true)
    public Decision<Issue> evaluateIssue(AgentJob job, DeliveryPolicyStage stage, @Nullable UUID feedbackId) {
        return evaluateIssue(job, stage, feedbackId, DeliveryPolicySurface.ARTIFACT, Set.of());
    }

    @Transactional(readOnly = true)
    public Decision<Issue> evaluateIssue(
        AgentJob job,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        Collection<String> contributingPracticeSlugs
    ) {
        return evaluateIssue(job, stage, feedbackId, DeliveryPolicySurface.ARTIFACT, contributingPracticeSlugs);
    }

    private Decision<Issue> evaluateIssue(
        AgentJob job,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        DeliveryPolicySurface surface,
        Collection<String> contributingPracticeSlugs
    ) {
        long workspaceId = requireWorkspaceId(job);
        Workspace workspace = activePracticeWorkspace(workspaceId);
        if (silentModeQuery.isSilentModeEngaged()) {
            Resolution resolution = resolve(
                job,
                workspace,
                null,
                null,
                false,
                null,
                stage,
                feedbackId,
                contributingPracticeSlugs,
                null,
                null,
                "scm.issue"
            );
            record(job, workspaceId, feedbackId, surface, stage, resolution);
            return Decision.suppressed(resolution.result().suppressionReason());
        }
        if (workspace == null) {
            Resolution resolution = resolve(
                job,
                null,
                null,
                null,
                false,
                null,
                stage,
                feedbackId,
                contributingPracticeSlugs,
                null,
                null,
                "scm.issue"
            );
            record(job, workspaceId, feedbackId, surface, stage, resolution);
            return Decision.suppressed(resolution.result().suppressionReason());
        }
        JsonNode metadata = job.getMetadata();
        Issue issue = integralId(metadata, "issue_id")
            .flatMap(issueRepository::findByIdWithAuthorAndRepository)
            .orElse(null);
        boolean target =
            workspace != null &&
            isEligibleTarget(issue, metadata, "issue_number", workspaceId) &&
            issue.getAuthor() != null;
        boolean closedWhenQueued = metadata != null && "closed".equalsIgnoreCase(metadata.path("state").asString(""));
        FeedbackSuppressionReason artifactRefusal = !target
            ? FeedbackSuppressionReason.ARTIFACT_GONE
            : closedWhenQueued || issue.getState() == Issue.State.CLOSED
                ? FeedbackSuppressionReason.ARTIFACT_CLOSED
                : null;
        CoverageAssessment coverage =
            workspace == null
                ? null
                : coverageService.assess(
                      workspace,
                      issue == null || issue.getRepository() == null ? null : issue.getRepository().getNameWithOwner(),
                      null,
                      issue == null ? null : issue.reviewSubject(),
                      false
                  );
        Resolution resolution = resolve(
            job,
            workspace,
            coverage,
            target ? recipientAllowsDelivery(issue) : null,
            artifactRefusal == null,
            artifactRefusal,
            stage,
            feedbackId,
            contributingPracticeSlugs,
            issue == null ? null : issue.getRepository().getNameWithOwner(),
            null,
            "scm.issue"
        );
        record(job, workspaceId, feedbackId, surface, stage, resolution);
        return resolution.result().allowed()
            ? Decision.allowed(issue)
            : Decision.suppressed(resolution.result().suppressionReason());
    }

    @Transactional(readOnly = true)
    public Decision<PullRequest> evaluatePullRequest(AgentJob job) {
        return evaluatePullRequest(job, DeliveryPolicyStage.AUTOMATIC, null);
    }

    @Transactional(readOnly = true)
    public Decision<PullRequest> evaluatePullRequest(AgentJob job, DeliveryPolicyStage stage, @Nullable UUID feedbackId) {
        return evaluatePullRequest(job, stage, feedbackId, DeliveryPolicySurface.ARTIFACT, Set.of());
    }

    @Transactional(readOnly = true)
    public Decision<PullRequest> evaluatePullRequest(
        AgentJob job,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        Collection<String> contributingPracticeSlugs
    ) {
        return evaluatePullRequest(job, stage, feedbackId, DeliveryPolicySurface.ARTIFACT, contributingPracticeSlugs);
    }

    private Decision<PullRequest> evaluatePullRequest(
        AgentJob job,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        DeliveryPolicySurface surface,
        Collection<String> contributingPracticeSlugs
    ) {
        long workspaceId = requireWorkspaceId(job);
        Workspace workspace = activePracticeWorkspace(workspaceId);
        if (silentModeQuery.isSilentModeEngaged()) {
            Resolution resolution = resolve(
                job,
                workspace,
                null,
                null,
                false,
                null,
                stage,
                feedbackId,
                contributingPracticeSlugs,
                null,
                null,
                "scm.pull_request"
            );
            record(job, workspaceId, feedbackId, surface, stage, resolution);
            return Decision.suppressed(resolution.result().suppressionReason());
        }
        if (workspace == null) {
            Resolution resolution = resolve(
                job,
                null,
                null,
                null,
                false,
                null,
                stage,
                feedbackId,
                contributingPracticeSlugs,
                null,
                null,
                "scm.pull_request"
            );
            record(job, workspaceId, feedbackId, surface, stage, resolution);
            return Decision.suppressed(resolution.result().suppressionReason());
        }
        JsonNode metadata = job.getMetadata();
        PullRequest pullRequest = integralId(metadata, "pull_request_id")
            .flatMap(pullRequestRepository::findByIdWithAuthorAndRepository)
            .orElse(null);
        boolean target =
            workspace != null &&
            isEligibleTarget(pullRequest, metadata, "pr_number", workspaceId) &&
            pullRequest.getAuthor() != null;
        PracticeReviewSettings settings = workspace == null ? null : workspace.getReviewSettings();
        FeedbackSuppressionReason artifactRefusal = !target
            ? FeedbackSuppressionReason.ARTIFACT_GONE
            : pullRequest.getState() == Issue.State.CLOSED
                ? FeedbackSuppressionReason.ARTIFACT_CLOSED
                : pullRequest.getState() == Issue.State.MERGED &&
                  !settings.resolveDeliverToMerged(reviewProperties.deliverToMerged())
                    ? FeedbackSuppressionReason.ARTIFACT_MERGED
                    : null;
        CoverageAssessment coverage =
            workspace == null
                ? null
                : coverageService.assess(
                      workspace,
                      pullRequest == null || pullRequest.getRepository() == null
                          ? null
                          : pullRequest.getRepository().getNameWithOwner(),
                      pullRequest == null ? null : pullRequest.getBaseRefName(),
                      pullRequest == null ? null : pullRequest.reviewSubject(),
                      true
                  );
        Resolution resolution = resolve(
            job,
            workspace,
            coverage,
            target ? recipientAllowsDelivery(pullRequest) : null,
            artifactRefusal == null,
            artifactRefusal,
            stage,
            feedbackId,
            contributingPracticeSlugs,
            pullRequest == null ? null : pullRequest.getRepository().getNameWithOwner(),
            pullRequest == null ? null : pullRequest.getBaseRefName(),
            "scm.pull_request"
        );
        record(job, workspaceId, feedbackId, surface, stage, resolution);
        return resolution.result().allowed()
            ? Decision.allowed(pullRequest)
            : Decision.suppressed(resolution.result().suppressionReason());
    }

    @Transactional(readOnly = true)
    public boolean allowsComposition(AgentJob job, DeliveryPolicySurface surface) {
        if (surface == DeliveryPolicySurface.ARTIFACT) {
            throw new IllegalArgumentException("Artifact composition must use its typed policy entry point");
        }
        JsonNode metadata = job.getMetadata();
        if (metadata != null && metadata.path("issue_id").isIntegralNumber()) {
            return evaluateIssue(job, DeliveryPolicyStage.COMPOSITION, null, surface, Set.of()).allowed();
        }
        if (metadata != null && metadata.path("pull_request_id").isIntegralNumber()) {
            return evaluatePullRequest(job, DeliveryPolicyStage.COMPOSITION, null, surface, Set.of()).allowed();
        }
        long workspaceId = requireWorkspaceId(job);
        Workspace workspace = activePracticeWorkspace(workspaceId);
        Resolution resolution = resolve(
            job,
            workspace,
            null,
            // Consent is not asked here: a kind without an artifact reaches nobody unsolicited, only the
            // developer's own page or a conversation they opened.
            null,
            null,
            null,
            DeliveryPolicyStage.COMPOSITION,
            null,
            Set.of(),
            null,
            null,
            job.getArtifactKind() == null ? null : job.getArtifactKind().value()
        );
        record(job, workspaceId, null, surface, DeliveryPolicyStage.COMPOSITION, resolution);
        return resolution.result().allowed();
    }

    static boolean matchesArtifact(Issue artifact, JsonNode metadata, String numberKey) {
        return (
            artifact.getDeletedAt() == null &&
            artifact.getRepository() != null &&
            artifact.getRepository().getId() != null &&
            metadata != null &&
            metadata.path("repository_id").isIntegralNumber() &&
            metadata.path("repository_id").asLong() == artifact.getRepository().getId() &&
            metadata.path("repository_full_name").isString() &&
            metadata.path("repository_full_name").asString().equals(artifact.getRepository().getNameWithOwner()) &&
            metadata.path(numberKey).isIntegralNumber() &&
            metadata.path(numberKey).asInt() == artifact.getNumber()
        );
    }

    private boolean isEligibleTarget(
        @Nullable Issue artifact,
        @Nullable JsonNode metadata,
        String numberKey,
        long workspaceId
    ) {
        return (
            artifact != null &&
            matchesArtifact(artifact, metadata, numberKey) &&
            repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(
                workspaceId,
                artifact.getRepository().getNameWithOwner()
            )
        );
    }

    private boolean recipientAllowsDelivery(Issue artifact) {
        return accountPreferencesQuery
            .preferencesForUserId(artifact.getAuthor().getId())
            .map(AccountPreferencesQuery.PreferencesView::practiceFeedbackDeliveryEnabled)
            .orElse(false);
    }

    private @Nullable Workspace activePracticeWorkspace(long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .filter(workspace -> workspace.getStatus() == Workspace.WorkspaceStatus.ACTIVE)
            .filter(workspace -> Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled()))
            .orElse(null);
    }

    private Resolution resolve(
        AgentJob job,
        @Nullable Workspace workspace,
        @Nullable CoverageAssessment coverage,
        @Nullable Boolean consent,
        @Nullable Boolean artifactEligible,
        @Nullable FeedbackSuppressionReason artifactRefusal,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        Collection<String> contributingPracticeSlugs,
        @Nullable String repository,
        @Nullable String baseBranch,
        @Nullable String artifactKind
    ) {
        Long admittedRevision = job.getPracticeRolloutRevision();
        Long evaluatedRevision = workspace == null ? null : workspace.getReviewSettings().getRolloutRevision();
        AutonomyAssessment autonomy = autonomy(workspace, stage, feedbackId, contributingPracticeSlugs);
        DeliveryPolicyResolver.Result result = DeliveryPolicyResolver.resolve(
            new DeliveryPolicyResolver.Facts(
                !silentModeQuery.isSilentModeEngaged(),
                workspace != null,
                workspace == null
                    ? null
                    : admittedRevision != null && admittedRevision.longValue() == evaluatedRevision,
                workspace == null
                    ? null
                    : workspace.getReviewSettings().getDeliveryStatus() == PracticeDeliveryStatus.ACTIVE,
                // No assessment means there is no artifact to judge, never an artifact left unjudged: the
                // artifact paths assess coverage whenever the workspace resolves, and a missing workspace
                // is already denied by WORKSPACE_ENABLED.
                coverage == null ? null : coverage.admitted(),
                autonomy.authorized(),
                consent,
                artifactEligible,
                artifactRefusal
            )
        );
        DeliveryPolicyFactsSnapshot snapshot = new DeliveryPolicyFactsSnapshot(
            artifactKind,
            repository,
            baseBranch,
            coverage == null ? null : subjectStatus(coverage.subjectStatus()),
            coverage == null ? null : coverage.repositoryMode(),
            coverage == null ? null : coverage.personMode(),
            coverage == null ? null : coverage.repositoryMatched(),
            coverage == null ? null : coverage.branchMatched(),
            coverage == null ? null : coverage.personMatched(),
            consent,
            workspace == null ? null : workspace.getReviewSettings().getDeliveryStatus(),
            job.getPracticeTriggerMode(),
            autonomy.facts()
        );
        return new Resolution(result, evaluatedRevision, snapshot);
    }

    private AutonomyAssessment autonomy(
        @Nullable Workspace workspace,
        DeliveryPolicyStage stage,
        @Nullable UUID feedbackId,
        Collection<String> contributingPracticeSlugs
    ) {
        if (workspace == null) return new AutonomyAssessment(false, List.of());
        // Composition runs before the contributing practices are known, so authority is not yet askable.
        // Answering false instead of null here refuses every composition and closes the lanes outright.
        if (feedbackId == null && contributingPracticeSlugs.isEmpty()) {
            return new AutonomyAssessment(null, List.of());
        }
        List<Practice> practices;
        int expected;
        if (feedbackId != null) {
            practices = practiceRepository.findContributingPractices(workspace.getId(), feedbackId);
            expected = practices.size();
        } else {
            Set<String> slugs = Set.copyOf(contributingPracticeSlugs);
            practices = slugs.isEmpty()
                ? List.of()
                : practiceRepository.findByWorkspaceIdAndSlugIn(workspace.getId(), slugs);
            expected = slugs.size();
        }
        PracticeAutonomy workspaceDefault = WorkspaceReviewDefaults.of(workspace).defaultAutonomy();
        List<DeliveryPolicyFactsSnapshot.PracticeFact> facts = practices
            .stream()
            .map(practice ->
                new DeliveryPolicyFactsSnapshot.PracticeFact(
                    practice.getSlug(),
                    AutonomyResolver.effectiveAutonomyOf(practice, workspaceDefault)
                )
            )
            .sorted(java.util.Comparator.comparing(DeliveryPolicyFactsSnapshot.PracticeFact::slug))
            .toList();
        boolean authorized =
            !facts.isEmpty() &&
            facts.size() == expected &&
            facts.stream().allMatch(fact -> fact.autonomy() == requiredAutonomy(stage, feedbackId));
        return new AutonomyAssessment(authorized, facts);
    }

    static PracticeAutonomy requiredAutonomy(DeliveryPolicyStage stage, @Nullable UUID feedbackId) {
        return isApprovedAttempt(stage, feedbackId) ? PracticeAutonomy.HUMAN_APPROVAL : PracticeAutonomy.AUTOMATIC;
    }

    private static boolean isApprovedAttempt(DeliveryPolicyStage stage, @Nullable UUID feedbackId) {
        return feedbackId != null && (stage == DeliveryPolicyStage.APPROVED || stage == DeliveryPolicyStage.EGRESS);
    }

    private static DeliveryPolicyFactsSnapshot.SubjectStatus subjectStatus(
        PracticeReviewCoverageService.SubjectStatus status
    ) {
        return DeliveryPolicyFactsSnapshot.SubjectStatus.valueOf(status.name());
    }

    private void record(
        AgentJob job,
        long workspaceId,
        @Nullable UUID feedbackId,
        DeliveryPolicySurface surface,
        DeliveryPolicyStage stage,
        Resolution resolution
    ) {
        evaluationRecorder.record(
            new DeliveryPolicyEvaluationCommand(
                workspaceId,
                job.getId(),
                feedbackId,
                job.getPracticeRolloutRevision() == null ? -1 : job.getPracticeRolloutRevision(),
                resolution.evaluatedRevision(),
                surface,
                stage,
                resolution.result(),
                resolution.facts()
            )
        );
    }

    /** {@code authorized} is null when there is no practice set to judge yet — see {@link #autonomy}. */
    private record AutonomyAssessment(
        @Nullable Boolean authorized,
        List<DeliveryPolicyFactsSnapshot.PracticeFact> facts
    ) {}

    private record Resolution(
        DeliveryPolicyResolver.Result result,
        @Nullable Long evaluatedRevision,
        DeliveryPolicyFactsSnapshot facts
    ) {}

    private static java.util.Optional<Long> integralId(@Nullable JsonNode metadata, String key) {
        if (metadata == null || !metadata.path(key).isIntegralNumber()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(metadata.path(key).asLong());
    }

    private static long requireWorkspaceId(AgentJob job) {
        if (job.getWorkspace() == null || job.getWorkspace().getId() == null) {
            throw new JobDeliveryException("Job has no workspace: jobId=" + job.getId());
        }
        return job.getWorkspace().getId();
    }

    record Decision<T extends Issue>(@Nullable T artifact, @Nullable FeedbackSuppressionReason suppressionReason) {
        static <T extends Issue> Decision<T> allowed(T artifact) {
            return new Decision<>(artifact, null);
        }

        static <T extends Issue> Decision<T> suppressed(FeedbackSuppressionReason reason) {
            return new Decision<>(null, reason);
        }

        boolean allowed() {
            return artifact != null;
        }
    }
}
