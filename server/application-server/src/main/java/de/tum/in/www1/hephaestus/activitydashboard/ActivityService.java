package de.tum.in.www1.hephaestus.activitydashboard;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ActivityService {

    private PullRequestRepository pullRequestRepository;

    private IssueRepository issueRepository;

    private PullRequestReviewRepository pullRequestReviewRepository;

    public ActivityService(PullRequestRepository pullRequestRepository, IssueRepository issueRepository, PullRequestReviewRepository pullRequestReviewRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
    }

    @Transactional
    public ActivityDTO getActivity(String login) {

        List<PullRequestInfoDTO> openPullRequests = pullRequestRepository
                .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN))
                .stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .toList();

        List<IssueInfoDTO> openIssues = issueRepository.findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN))
                .stream()
                .map(IssueInfoDTO::fromIssue)
                .toList();

        List<ReviewActivityDTO> reviews = getReviewedOrRequestedPullRequests(login);

        return new ActivityDTO(
                openPullRequests,
                openIssues,
                reviews
        );
    }

    private List<ReviewActivityDTO> getReviewedOrRequestedPullRequests(String login) {
        Stream<ReviewActivityDTO> reviews = pullRequestReviewRepository.findAllOpenReviewsByAuthorLogin(login)
                .stream()
                .map(ReviewActivityDTO::fromPullRequestReview);

        Stream<ReviewActivityDTO> requestedReviews = pullRequestRepository.findOpenReviewRequestedByLogin(login)
                .stream()
                .map(ReviewActivityDTO::fromPullRequest);

        return Stream.concat(reviews, requestedReviews).toList();
    }
}
