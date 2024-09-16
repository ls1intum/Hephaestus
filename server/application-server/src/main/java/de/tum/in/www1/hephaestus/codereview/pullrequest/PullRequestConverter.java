package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class PullRequestConverter extends BaseGitServiceEntityConverter<GHPullRequest, PullRequest> {

    protected static final Logger logger = LoggerFactory.getLogger(PullRequestConverter.class);

    @Override
    public PullRequest convert(@NonNull GHPullRequest source) {
        IssueState state = convertState(source.getState());
        PullRequest pullRequest = new PullRequest();
        convertBaseFields(source, pullRequest);
        pullRequest.setNumber(source.getNumber());
        pullRequest.setTitle(source.getTitle());
        pullRequest.setUrl(source.getHtmlUrl().toString());
        pullRequest.setState(state);
        pullRequest.setNumber(source.getNumber());
        pullRequest.setPullRequestLabels(convertLabels(source.getLabels()));
        try {
            pullRequest.setAdditions(source.getAdditions());
        } catch (IOException e) {
            logger.error("Failed to convert additions field for source {}: {}", source.getId(), e.getMessage());
            pullRequest.setAdditions(0);
        }
        try {
            pullRequest.setDeletions(source.getDeletions());
        } catch (IOException e) {
            logger.error("Failed to convert deletions field for source {}: {}", source.getId(), e.getMessage());
            pullRequest.setDeletions(0);
        }
        try {
            pullRequest.setCommits(source.getCommits());
        } catch (IOException e) {
            logger.error("Failed to convert commits field for source {}: {}", source.getId(), e.getMessage());
            pullRequest.setCommits(0);
        }
        try {
            pullRequest.setChangedFiles(source.getChangedFiles());
        } catch (IOException e) {
            logger.error("Failed to convert changedFiles field for source {}: {}", source.getId(), e.getMessage());
            pullRequest.setChangedFiles(0);
        }
        if (source.getMergedAt() != null) {
            pullRequest.setMergedAt(convertToOffsetDateTime(source.getMergedAt()));
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

    private Set<PullRequestLabel> convertLabels(Collection<GHLabel> labels) {
        Set<PullRequestLabel> pullRequestLabels = new HashSet<>();
        for (GHLabel label : labels) {
            PullRequestLabel pullRequestLabel = new PullRequestLabel();
            pullRequestLabel.setName(label.getName());
            pullRequestLabel.setColor(label.getColor());
            pullRequestLabels.add(pullRequestLabel);
        }
        return pullRequestLabels;
    }
}
