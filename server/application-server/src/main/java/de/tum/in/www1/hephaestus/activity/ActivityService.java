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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityService.class);

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @Autowired
    private BadPracticeFeedbackRepository badPracticeFeedbackRepository;

    @Autowired
    private BadPracticeDetectionRepository badPracticeDetectionRepository;

    @Autowired
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Value("${hephaestus.detection.tracing.enabled}")
    private boolean tracingEnabled;

    @Value("${hephaestus.detection.tracing.host}")
    private String tracingHost;

    @Value("${hephaestus.detection.tracing.public-key}")
    private String tracingPublicKey;

    @Value("${hephaestus.detection.tracing.secret-key}")
    private String tracingSecretKey;

    public ActivityDTO getActivity(String login) {
        logger.info("Getting activity for user with login: {}", login);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN)
        );

        List<PullRequestWithBadPracticesDTO> openPullRequestsWithBadPractices = pullRequests
            .stream()
            .map(pullRequest -> {
                List<PullRequestBadPracticeDTO> badPractices = badPracticeDetectionRepository
                    .findMostRecentByPullRequestId(pullRequest.getId())
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

                return PullRequestWithBadPracticesDTO.fromPullRequest(pullRequest, badPractices, oldBadPractices);
            })
            .collect(Collectors.toList());

        return new ActivityDTO(openPullRequestsWithBadPractices);
    }

    public DetectionResult detectBadPracticesForUser(String login) {
        logger.info("Detecting bad practices for user with login: {}", login);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN)
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

    public DetectionResult detectBadPracticesForPullRequest(PullRequest pullRequest) {
        logger.info("Detecting bad practices for PR: {}", pullRequest.getId());

        return pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequest);
    }

    public void resolveBadPractice(PullRequestBadPractice badPractice, PullRequestBadPracticeState state) {
        logger.info("Resolving bad practice {} with state {}", badPractice.getId(), state);

        badPractice.setUserState(state);
        pullRequestBadPracticeRepository.save(badPractice);
    }

    public void provideFeedbackForBadPractice(PullRequestBadPractice badPractice, BadPracticeFeedbackDTO feedback) {
        logger.info("Marking bad practice with id: {}", badPractice.getId());

        BadPracticeFeedback badPracticeFeedback = new BadPracticeFeedback();
        badPracticeFeedback.setPullRequestBadPractice(badPractice);
        badPracticeFeedback.setExplanation(feedback.explanation());
        badPracticeFeedback.setType(feedback.type());
        badPracticeFeedback.setCreationTime(OffsetDateTime.now());
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
                .value(CreateScoreValue.of(feedback.explanation()))
                .build();

            CreateScoreResponse response = client.score().create(request);
            logger.info("Feedback sent to Langfuse: {}", response.toString());
        } catch (Exception e) {
            logger.error("Failed to send feedback to Langfuse: {}", e.getMessage());
        }
    }
}
