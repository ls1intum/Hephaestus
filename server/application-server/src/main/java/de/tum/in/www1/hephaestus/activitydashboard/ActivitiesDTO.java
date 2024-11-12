package de.tum.in.www1.hephaestus.activitydashboard;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewInfoDTO;
import io.micrometer.common.lang.NonNull;

import java.util.List;

public record ActivitiesDTO(
        @NonNull List<PullRequestInfoDTO> pullRequests,
        @NonNull List<IssueInfoDTO> issues,
        @NonNull List<PullRequestReviewInfoDTO> reviews) {
}
