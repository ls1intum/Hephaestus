package de.tum.in.www1.hephaestus.codereview.pullrequest.review;

import java.io.IOException;

import org.kohsuke.github.GHPullRequestReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class PullRequestReviewConverter extends BaseGitServiceEntityConverter<GHPullRequestReview, PullRequestReview> {

    protected static final Logger logger = LoggerFactory.getLogger(PullRequestReviewConverter.class);

    @Override
    public PullRequestReview convert(@NonNull GHPullRequestReview source) {
        PullRequestReview review = new PullRequestReview();
        convertBaseFields(source, review);
        review.setState(convertState(source.getState()));
        try {
            review.setSubmittedAt(convertToOffsetDateTime(source.getSubmittedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert submittedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        return review;
    }

    private PullRequestReviewState convertState(org.kohsuke.github.GHPullRequestReviewState state) {
        switch (state) {
            case COMMENTED:
                return PullRequestReviewState.COMMENTED;
            case APPROVED:
                return PullRequestReviewState.APPROVED;
            case CHANGES_REQUESTED:
                return PullRequestReviewState.CHANGES_REQUESTED;
            case DISMISSED:
                return PullRequestReviewState.DISMISSED;
            default:
                return PullRequestReviewState.COMMENTED;
        }
    }
}
