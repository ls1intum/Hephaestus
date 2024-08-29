package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.io.IOException;

import org.kohsuke.github.GHPullRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@ReadingConverter
public class PullRequestConverter implements Converter<GHPullRequest, PullRequest> {
    @Override
    public PullRequest convert(@NonNull GHPullRequest source) {
        PullRequest pullrequest = new PullRequest();
        pullrequest.setGithubId(source.getId());
        pullrequest.setTitle(source.getTitle());
        pullrequest.setUrl(source.getHtmlUrl().toString());
        pullrequest.setState(convertState(source.getState()));
        try {
            pullrequest.setCreatedAt(source.getCreatedAt().toString());
        } catch (IOException e) {
            // find a better way to handle this
            pullrequest.setCreatedAt(null);
        }
        try {
            pullrequest.setUpdatedAt(source.getUpdatedAt().toString());
        } catch (IOException e) {
            // find a better way to handle this
            pullrequest.setUpdatedAt(null);
        }
        pullrequest.setMergedAt(source.getMergedAt() != null ? source.getMergedAt().toString() : null);
        // set preliminary values to be filled in later
        pullrequest.setAuthor(null);
        pullrequest.setRepository(null);
        return pullrequest;
    }

    private GHIssueState convertState(org.kohsuke.github.GHIssueState state) {
        switch (state) {
            case OPEN:
                return GHIssueState.OPEN;
            case CLOSED:
                return GHIssueState.CLOSED;
            case ALL:
                return GHIssueState.ALL;
            default:
                return GHIssueState.ALL;
        }
    }

}
