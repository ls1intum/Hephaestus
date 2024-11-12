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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ActivityDashboardService {

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PullRequestReviewRepository pullRequestReviewRepository;

    public ActivitiesDTO getActivities(String login) {

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(login, Set.of(PullRequest.State.OPEN));
        List<Issue> issues = List.of();//issueRepository.findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN));
        List<PullRequestReview> reviews = List.of(); //TODO: which reviews to return here: all reviews of open PRs of others? what  about where review is requested?
        return new ActivitiesDTO(
                pullRequests.stream().map(PullRequestInfoDTO::fromPullRequest).toList(),
                issues.stream().map(IssueInfoDTO::fromIssue).toList(),
                reviews.stream().map(PullRequestReviewInfoDTO::fromPullRequestReview).toList()
        );
    }
}
