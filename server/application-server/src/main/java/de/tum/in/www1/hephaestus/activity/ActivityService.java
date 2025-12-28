package de.tum.in.www1.hephaestus.activity;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import com.langfuse.client.resources.score.types.CreateScoreResponse;
import de.tum.in.www1.hephaestus.activity.badpracticedetector.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for managing user activity and bad practice detection within workspaces.
 */
@Service
public class ActivityService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityService.class);

    private final PullRequestRepository pullRequestRepository;
    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final BadPracticeFeedbackRepository badPracticeFeedbackRepository;
    private final BadPracticeDetectionRepository badPracticeDetectionRepository;
    private final PullRequestBadPracticeDetector pullRequestBadPracticeDetector;
    private final boolean tracingEnabled;
    private final String tracingHost;
    private final String tracingPublicKey;
    private final String tracingSecretKey;

    public ActivityService(
        PullRequestRepository pullRequestRepository,
        PullRequestBadPracticeRepository pullRequestBadPracticeRepository,
        BadPracticeFeedbackRepository badPracticeFeedbackRepository,
        BadPracticeDetectionRepository badPracticeDetectionRepository,
        PullRequestBadPracticeDetector pullRequestBadPracticeDetector,
        @Value("${hephaestus.detection.tracing.enabled}") boolean tracingEnabled,
        @Value("${hephaestus.detection.tracing.host}") String tracingHost,
        @Value("${hephaestus.detection.tracing.public-key}") String tracingPublicKey,
        @Value("${hephaestus.detection.tracing.secret-key}") String tracingSecretKey
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestBadPracticeRepository = pullRequestBadPracticeRepository;
        this.badPracticeFeedbackRepository = badPracticeFeedbackRepository;
        this.badPracticeDetectionRepository = badPracticeDetectionRepository;
        this.pullRequestBadPracticeDetector = pullRequestBadPracticeDetector;
        this.tracingEnabled = tracingEnabled;
        this.tracingHost = tracingHost;
        this.tracingPublicKey = tracingPublicKey;
        this.tracingSecretKey = tracingSecretKey;
    }

    @Transactional
    public ActivityDTO getActivity(Workspace workspace, String login) {
        logger.info("Getting activity for user with login: {} in workspace {}", login, workspace.getWorkspaceSlug());

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN),
            workspace.getId()
        );

        List<PullRequestWithBadPracticesDTO> openPullRequestsWithBadPractices = pullRequests
            .stream()
            .map(pullRequest -> {
                BadPracticeDetection lastDetection = badPracticeDetectionRepository.findMostRecentByPullRequestId(
                    pullRequest.getId()
                );

                List<PullRequestBadPracticeDTO> badPractices = lastDetection == null
                    ? List.of()
                    : lastDetection
                        .getBadPractices()
                        .stream()
                        .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
                        .toList();

                List<String> badPracticeTitles = badPractices.stream().map(PullRequestBadPracticeDTO::title).toList();

                List<PullRequestBadPracticeDTO> oldBadPractices = pullRequestBadPracticeRepository
                    .findByPullRequestId(pullRequest.getId())
                    .stream()
                    .filter(badPractice -> !badPracticeTitles.contains(badPractice.getTitle()))
                    .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
                    .toList();

                String summary = lastDetection != null ? lastDetection.getSummary() : "";
                return PullRequestWithBadPracticesDTO.fromPullRequest(
                    pullRequest,
                    summary,
                    badPractices,
                    oldBadPractices
                );
            })
            .collect(Collectors.toList());

        return new ActivityDTO(openPullRequestsWithBadPractices);
    }

    @Transactional
    public DetectionResult detectBadPracticesForUser(Workspace workspace, String login) {
        logger.info(
            "Detecting bad practices for user with login: {} in workspace {}",
            login,
            workspace.getWorkspaceSlug()
        );

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN),
            workspace.getId()
        );

        List<DetectionResult> detectedBadPractices = new ArrayList<>();
        for (PullRequest pullRequest : pullRequests) {
            detectedBadPractices.add(pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequest));
        }
        if (detectedBadPractices.stream().anyMatch(result -> result == DetectionResult.BAD_PRACTICES_DETECTED)) {
            return DetectionResult.BAD_PRACTICES_DETECTED;
        } else if (
            detectedBadPractices.stream().anyMatch(result -> result == DetectionResult.NO_BAD_PRACTICES_DETECTED)
        ) {
            return DetectionResult.NO_BAD_PRACTICES_DETECTED;
        } else {
            return DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST;
        }
    }

    @Transactional
    public DetectionResult detectBadPracticesForPullRequest(Workspace workspace, PullRequest pullRequest) {
        logger.info(
            "Detecting bad practices for PR: {} in workspace {}",
            pullRequest.getId(),
            workspace.getWorkspaceSlug()
        );
        return pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequest);
    }

    public void resolveBadPractice(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        PullRequestBadPracticeState state
    ) {
        logger.info(
            "Resolving bad practice {} with state {} in workspace {}",
            badPractice.getId(),
            state,
            workspace.getWorkspaceSlug()
        );
        badPractice.setUserState(state);
        pullRequestBadPracticeRepository.save(badPractice);
    }

    public void provideFeedbackForBadPractice(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        BadPracticeFeedbackDTO feedback
    ) {
        logger.info(
            "Providing feedback for bad practice {} in workspace {}",
            badPractice.getId(),
            workspace.getWorkspaceSlug()
        );

        BadPracticeFeedback badPracticeFeedback = new BadPracticeFeedback();
        badPracticeFeedback.setPullRequestBadPractice(badPractice);
        badPracticeFeedback.setExplanation(feedback.explanation());
        badPracticeFeedback.setType(feedback.type());
        badPracticeFeedback.setCreationTime(Instant.now());
        badPracticeFeedbackRepository.save(badPracticeFeedback);

        if (tracingEnabled && badPractice.getDetectionTraceId() != null) {
            sendFeedbackToLangfuse(badPractice, feedback);
        }
    }

    @Async
    protected void sendFeedbackToLangfuse(PullRequestBadPractice badPractice, BadPracticeFeedbackDTO feedback) {
        logger.info("Sending feedback to Langfuse for bad practice: {}", badPractice.getId());
        try {
            LangfuseClient client = LangfuseClient.builder()
                .url(tracingHost)
                .credentials(tracingPublicKey, tracingSecretKey)
                .build();

            CreateScoreRequest request = CreateScoreRequest.builder()
                .traceId(badPractice.getDetectionTraceId())
                .name("user_feedback")
                .value(CreateScoreValue.of(feedback.type()))
                .comment(
                    String.format("Bad practice: %s - Feedback: %s", badPractice.getTitle(), feedback.explanation())
                )
                .build();

            CreateScoreResponse response = client.score().create(request);
            logger.info("Feedback sent to Langfuse: {}", response.toString());
        } catch (Exception e) {
            logger.error("Failed to send feedback to Langfuse: {}", e.getMessage());
        }
    }
}
