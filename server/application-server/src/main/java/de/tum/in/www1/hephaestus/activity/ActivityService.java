package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.badpractice.BadPracticeDetectionRepository;
import de.tum.in.www1.hephaestus.activity.badpractice.BadPracticeFeedbackService;
import de.tum.in.www1.hephaestus.activity.badpractice.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.badpracticedetector.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for retrieving user activity and orchestrating bad practice detection within workspaces.
 * Delegates feedback handling to {@link BadPracticeFeedbackService}.
 */
@Service
public class ActivityService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityService.class);

    private final PullRequestRepository pullRequestRepository;
    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final BadPracticeDetectionRepository badPracticeDetectionRepository;
    private final PullRequestBadPracticeDetector pullRequestBadPracticeDetector;
    private final BadPracticeFeedbackService badPracticeFeedbackService;

    public ActivityService(
        PullRequestRepository pullRequestRepository,
        PullRequestBadPracticeRepository pullRequestBadPracticeRepository,
        BadPracticeDetectionRepository badPracticeDetectionRepository,
        PullRequestBadPracticeDetector pullRequestBadPracticeDetector,
        BadPracticeFeedbackService badPracticeFeedbackService
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestBadPracticeRepository = pullRequestBadPracticeRepository;
        this.badPracticeDetectionRepository = badPracticeDetectionRepository;
        this.pullRequestBadPracticeDetector = pullRequestBadPracticeDetector;
        this.badPracticeFeedbackService = badPracticeFeedbackService;
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
        badPracticeFeedbackService.resolveBadPractice(workspace, badPractice, state);
    }

    public void provideFeedbackForBadPractice(
        Workspace workspace,
        PullRequestBadPractice badPractice,
        BadPracticeFeedbackDTO feedback
    ) {
        badPracticeFeedbackService.provideFeedback(workspace, badPractice, feedback);
    }
}
