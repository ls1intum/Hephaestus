package de.tum.in.www1.hephaestus.codereview.pullrequest;

import org.kohsuke.github.GHPullRequest;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Service
@ReadingConverter
public class PullRequestConverter extends BaseGitServiceEntityConverter<GHPullRequest, PullRequest> {
    @Override
    public PullRequest convert(@NonNull GHPullRequest source) {
        IssueState state = convertState(source.getState());
        PullRequest pullRequest = new PullRequest();
        convertBaseFields(source, pullRequest);
        pullRequest.setTitle(source.getTitle());
        pullRequest.setUrl(source.getHtmlUrl().toString());
        pullRequest.setState(state);
        if (source.getMergedAt() != null) {
            pullRequest.setMergedAt(source.getMergedAt().toString());
        }
        return pullRequest;
    }

    private IssueState convertState(org.kohsuke.github.GHIssueState state) {
        switch (state) {
            case OPEN:
                return IssueState.OPEN;
            case CLOSED:
                return IssueState.CLOSED;
            default:
                return IssueState.OPEN;
        }
    }

}
