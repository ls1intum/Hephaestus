package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto;

import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserDTOConverter;

@Component
public class PullRequestReviewDTOConverter {

    private final UserDTOConverter userDTOConverter;
    private final PullRequestDTOConverter pullRequestDTOConverter;

    public PullRequestReviewDTOConverter(UserDTOConverter userDTOConverter,
            PullRequestDTOConverter pullRequestDTOConverter) {
        this.userDTOConverter = userDTOConverter;
        this.pullRequestDTOConverter = pullRequestDTOConverter;
    }

    public PullRequestReviewInfoDTO convertToDTO(PullRequestReview pullRequestReview) {
        return new PullRequestReviewInfoDTO(
                pullRequestReview.getId(),
                pullRequestReview.isDismissed(),
                pullRequestReview.getState(),
                pullRequestReview.getComments().size(),
                userDTOConverter.convertToDTO(pullRequestReview.getAuthor()),
                pullRequestDTOConverter.convertToBaseDTO(pullRequestReview.getPullRequest()),
                pullRequestReview.getHtmlUrl(),
                pullRequestReview.getSubmittedAt());
    }
}
