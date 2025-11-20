package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestReviewConverter implements Converter<GHPullRequestReview, PullRequestReview> {

    // no logger needed at the moment

    @Override
    public PullRequestReview convert(@NonNull GHPullRequestReview source) {
        return update(source, new PullRequestReview());
    }

    public PullRequestReview update(@NonNull GHPullRequestReview source, @NonNull PullRequestReview review) {
        review.setId(source.getId());
        review.setBody(sanitizeText(source.getBody()));

        if (review.getState() == null || source.getState() != GHPullRequestReviewState.DISMISSED) {
            review.setState(convertState(source.getState()));
        }
        // Only update dismissed state if it was not already dismissed
        if (!review.isDismissed()) {
            review.setDismissed(source.getState() == GHPullRequestReviewState.DISMISSED);
        }

        review.setHtmlUrl(source.getHtmlUrl().toString());
        if (source.getSubmittedAt() != null) {
            review.setSubmittedAt(source.getSubmittedAt());
        }
        review.setCommitId(source.getCommitId());
        return review;
    }

    private String sanitizeText(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\u0000", "");
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
