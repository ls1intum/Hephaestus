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
        PullRequest pullrequest = new PullRequest();
        convertBaseFields(source, pullrequest);
        pullrequest.setTitle(source.getTitle());
        pullrequest.setUrl(source.getHtmlUrl().toString());
        pullrequest.setState(state);
        if (source.getMergedAt() != null) {
            pullrequest.setMergedAt(source.getMergedAt().toString());
        }
        return pullrequest;
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
