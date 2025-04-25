package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.io.IOException;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestConverter extends BaseGitServiceEntityConverter<GHPullRequest, PullRequest> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestConverter.class);

    private final GitHubIssueConverter issueConverter;

    public GitHubPullRequestConverter(GitHubIssueConverter issueConverter) {
        this.issueConverter = issueConverter;
    }

    @Override
    public PullRequest convert(@NonNull GHPullRequest source) {
        return update(source, new PullRequest());
    }

    @Override
    public PullRequest update(@NonNull GHPullRequest source, @NonNull PullRequest pullRequest) {
        issueConverter.update(source, pullRequest);

        pullRequest.setMergedAt(DateUtil.convertToOffsetDateTime(source.getMergedAt()));
        try {
            pullRequest.setMergeCommitSha(source.getMergeCommitSha());
        } catch (IOException e) {
            logger.error("Failed to convert mergeCommitSha field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            pullRequest.setDraft(source.isDraft());
        } catch (IOException e) {
            logger.error("Failed to convert draft field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            pullRequest.setMerged(source.isMerged());
        } catch (IOException e) {
            logger.error("Failed to convert merged field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            if (source.getMergeable() != null) {
                pullRequest.setIsMergeable(Boolean.TRUE.equals(source.getMergeable()));
            }
        } catch (IOException e) {
            logger.error("Failed to convert mergeable field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            pullRequest.setMergeableState(source.getMergeableState());
        } catch (IOException e) {
            logger.error("Failed to convert mergeableState field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            pullRequest.setMaintainerCanModify(source.canMaintainerModify());
        } catch (IOException e) {
            logger.error(
                "Failed to convert maintainerCanModify field for source {}: {}",
                source.getId(),
                e.getMessage()
            );
        }
        try {
            pullRequest.setCommits(source.getCommits());
        } catch (IOException e) {
            logger.error("Failed to convert commits field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            if (pullRequest.getAdditions() == 0 || source.getAdditions() != 0) {
                pullRequest.setAdditions(source.getAdditions());
            }
        } catch (IOException e) {
            logger.error("Failed to convert additions field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            if (pullRequest.getDeletions() == 0 || source.getDeletions() != 0) {
                pullRequest.setDeletions(source.getDeletions());
            }
        } catch (IOException e) {
            logger.error("Failed to convert deletions field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            pullRequest.setChangedFiles(source.getChangedFiles());
        } catch (IOException e) {
            logger.error("Failed to convert changedFiles field for source {}: {}", source.getId(), e.getMessage());
        }

        return pullRequest;
    }
}
