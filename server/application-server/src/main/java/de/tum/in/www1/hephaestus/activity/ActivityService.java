package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    public ActivityDTO getActivity(String login) {
        logger.info("Getting activity for user with login: {}", login);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN)
        );

        List<PullRequestBadPractice> openPulLRequestBadPractices =
            pullRequestBadPracticeRepository.findAssignedByLoginAndOpen(login);

        Map<PullRequest, List<PullRequestBadPracticeDTO>> pullRequestBadPracticesMap = openPulLRequestBadPractices
            .stream()
            .collect(
                Collectors.groupingBy(
                    PullRequestBadPractice::getPullrequest,
                    Collectors.collectingAndThen(Collectors.toList(), list ->
                        list.stream().map(PullRequestBadPracticeDTO::fromPullRequestBadPractice).toList()
                    )
                )
            );

        List<PullRequestWithBadPracticesDTO> openPullRequestsWithBadPractices = pullRequests
            .stream()
            .map(pullRequest ->
                PullRequestWithBadPracticesDTO.fromPullRequest(
                    pullRequest,
                    pullRequestBadPracticesMap.getOrDefault(pullRequest, List.of())
                )
            )
            .collect(Collectors.toList());

        return new ActivityDTO(openPullRequestsWithBadPractices);
    }

    public List<PullRequestBadPracticeDTO> detectBadPracticesForUser(String login) {
        logger.info("Detecting bad practices for user with login: {}", login);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN)
        );

        List<PullRequestBadPractice> detectedBadPractices = new ArrayList<>();
        for (PullRequest pullRequest : pullRequests) {
            detectedBadPractices.addAll(pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequest));
        }
        return detectedBadPractices.stream().map(PullRequestBadPracticeDTO::fromPullRequestBadPractice).toList();
    }

    public List<PullRequestBadPracticeDTO> detectBadPracticesForPullRequest(PullRequest pullRequest) {
        logger.info("Detecting bad practices for PR: {}", pullRequest.getId());

        List<PullRequestBadPractice> detectedBadPractices = pullRequestBadPracticeDetector.detectAndSyncBadPractices(
            pullRequest
        );

        return detectedBadPractices.stream().map(PullRequestBadPracticeDTO::fromPullRequestBadPractice).toList();
    }

    public void resolveBadPractice(PullRequestBadPractice badPractice, PullRequestBadPracticeState state) {
        logger.info("Resolving bad practice {} with state {}", badPractice.getId(), state);

        badPractice.setUserState(state);
        pullRequestBadPracticeRepository.save(badPractice);
    }

    public void provideFeedbackForBadPractice(PullRequestBadPractice badPractice, BadPracticeFeedbackDTO feedback) {
        logger.info("Marking bad practice with id: {}", badPractice.getId());

        badPractice.setUserState(PullRequestBadPracticeState.WRONG);
        pullRequestBadPracticeRepository.save(badPractice);

        BadPracticeFeedback badPracticeFeedback = new BadPracticeFeedback();
        badPracticeFeedback.setPullRequestBadPractice(badPractice);
        badPracticeFeedback.setExplanation(feedback.explanation());
        badPracticeFeedback.setType(feedback.type());
        badPracticeFeedback.setCreationTime(OffsetDateTime.now());
        badPracticeFeedbackRepository.save(badPracticeFeedback);
    }
}
