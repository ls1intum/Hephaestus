package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;

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
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Autowired
    private UserRepository userRepository;

    @Transactional
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

    @Transactional
    public List<PullRequestBadPracticeDTO> detectBadPractices(String login) {
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


}
