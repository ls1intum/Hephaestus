package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use {@link GitHubIssueProcessor} with DTOs instead.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
@Component
public class GitHubIssueConverter extends BaseGitServiceEntityConverter<GHIssue, Issue> {

    @Override
    public Issue convert(@NonNull GHIssue source) {
        return update(source, new Issue());
    }

    @Override
    public Issue update(@NonNull GHIssue source, @NonNull Issue issue) {
        convertBaseFields(source, issue);
        issue.setNumber(source.getNumber());
        issue.setState(convertState(source.getState()));
        issue.setTitle(sanitizeForPostgres(source.getTitle()));
        issue.setBody(sanitizeForPostgres(source.getBody()));
        issue.setHtmlUrl(source.getHtmlUrl().toString());
        issue.setLocked(source.isLocked());
        issue.setClosedAt(source.getClosedAt());
        issue.setCommentsCount(source.getCommentsCount());
        issue.setHasPullRequest(source.getPullRequest() != null);
        issue.setStateReason(convertStateReason(source.getStateReason()));
        return issue;
    }

    private Issue.State convertState(GHIssueState state) {
        switch (state) {
            case OPEN:
                return Issue.State.OPEN;
            case CLOSED:
                return Issue.State.CLOSED;
            default:
                return Issue.State.CLOSED;
        }
    }

    Issue.StateReason convertStateReason(GHIssueStateReason stateReason) {
        if (stateReason == null) {
            return null;
        }

        return switch (stateReason) {
            case COMPLETED -> Issue.StateReason.COMPLETED;
            case NOT_PLANNED -> Issue.StateReason.NOT_PLANNED;
            case REOPENED -> Issue.StateReason.REOPENED;
            case UNKNOWN -> Issue.StateReason.UNKNOWN;
        };
    }
}
