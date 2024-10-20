package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import java.io.IOException;
import java.time.ZoneOffset;

import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;

@Component
public class GitHubPullRequestReviewConverter implements Converter<GHPullRequestReview, PullRequestReview> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewConverter.class);

    @Override
    public PullRequestReview convert(@NonNull GHPullRequestReview source) {
        return update(source, new PullRequestReview());
    }

    public PullRequestReview update(@NonNull GHPullRequestReview source, @NonNull PullRequestReview review) {
        review.setId(source.getId());
        review.setBody(source.getBody());
        
        if (review.getState() == null || source.getState() != GHPullRequestReviewState.DISMISSED) {
            review.setState(convertState(source.getState()));
        }
        review.setDismissed(source.getState() == GHPullRequestReviewState.DISMISSED);
        
        review.setHtmlUrl(source.getHtmlUrl().toString());
        try {
            if (source.getSubmittedAt() != null) {
                review.setSubmittedAt(source.getSubmittedAt().toInstant().atOffset(ZoneOffset.UTC));
            }
        } catch (IOException e) {
            logger.error("Failed to convert submittedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        review.setCommitId(source.getCommitId());
        return review;
    }

    private PullRequestReview.State convertState(org.kohsuke.github.GHPullRequestReviewState state) {
        switch (state) {
            case COMMENTED:
                return PullRequestReview.State.COMMENTED;
            case APPROVED:
                return PullRequestReview.State.APPROVED;
            case CHANGES_REQUESTED:
                return PullRequestReview.State.CHANGES_REQUESTED;
            default:
                return PullRequestReview.State.UNKNOWN;
        }
    }
}
