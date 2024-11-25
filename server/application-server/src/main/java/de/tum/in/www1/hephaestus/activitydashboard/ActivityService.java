package de.tum.in.www1.hephaestus.activitydashboard;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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

        List<ReviewActivityDto> reviews = getReviewedOrRequestedPullRequests(login);

        return new ActivityDTO(
                openPullRequests,
                openIssues,
                reviews
        );
    }

    private List<ReviewActivityDto> getReviewedOrRequestedPullRequests(String login) {
        List<ReviewActivityDto> reviews = pullRequestReviewRepository.findAllOpenReviewsByAuthorLogin(login)
                .stream()
                .map(ReviewActivityDto::fromPullRequestReview)
                .toList();

        List<ReviewActivityDto> requestedReviews = pullRequestRepository.findReviewRequestedByLogin(login)
                .stream()
                .map(ReviewActivityDto::fromPullRequest)
                .toList();

        requestedReviews.addAll(reviews);
        return requestedReviews;
    }
}
