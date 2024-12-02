package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @Transactional
    public ActivityDTO getActivity(String login) {

        List<PullRequest> pullRequests = pullRequestRepository
                .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN));

        List<PullRequestBadPractice> openPulLRequestBadPractices = pullRequestBadPracticeRepository.findByLogin(login);

        Map<PullRequest, List<PullRequestBadPracticeDTO>> pullRequestBadPracticesMap = openPulLRequestBadPractices.stream()
                .collect(Collectors.groupingBy(
                        PullRequestBadPractice::getPullrequest,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .map(PullRequestBadPractice::getType)
                                        .distinct()
                                        .map(PullRequestBadPracticeDTO::fromPullRequestBadPracticeType)
                                        .collect(Collectors.toList())
                        )
                ));

        List<PullRequestWithBadPracticesDTO> openPullRequestsWithBadPractices = pullRequests.stream()
                .map(pullRequest -> PullRequestWithBadPracticesDTO.fromPullRequest(pullRequest,
                        pullRequestBadPracticesMap.getOrDefault(pullRequest, List.of()))
                )
                .collect(Collectors.toList());

        return new ActivityDTO(openPullRequestsWithBadPractices);
    }
}
