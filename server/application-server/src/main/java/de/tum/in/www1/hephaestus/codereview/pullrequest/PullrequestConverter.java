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
        Long id = source.getId();
        String title = source.getTitle();
        String url = source.getHtmlUrl().toString();
        String mergedAt = source.getMergedAt() != null ? source.getMergedAt().toString() : null;
        IssueState state = convertState(source.getState());
        PullRequest pullrequest;
        try {
            String createdAt = source.getCreatedAt().toString();
            String updatedAt = source.getUpdatedAt().toString();
            pullrequest = new PullRequest(id, title, url, state, createdAt, updatedAt, mergedAt);
        } catch (IOException e) {
            pullrequest = new PullRequest(id, title, url, state, mergedAt, null, null);
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
