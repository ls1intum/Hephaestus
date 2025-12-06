package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueConverter;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubIssueCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentSyncService.class);

    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;
    private final GitHubIssueCommentConverter issueCommentConverter;
    private final GitHubIssueConverter issueConverter;
    private final GitHubUserSyncService userSyncService;

    public GitHubIssueCommentSyncService(
        IssueCommentRepository issueCommentRepository,
        IssueRepository issueRepository,
        GitHubIssueCommentConverter issueCommentConverter,
        GitHubIssueConverter issueConverter,
        GitHubUserSyncService userSyncService
    ) {
        this.issueCommentRepository = issueCommentRepository;
        this.issueRepository = issueRepository;
        this.issueCommentConverter = issueCommentConverter;
        this.issueConverter = issueConverter;
        this.userSyncService = userSyncService;
    }

    /**
     * Syncs all issue comments from the specified list of GitHub issues.
     *
     * @param ghIssues The GitHub issues to sync the comments of.
     */
    public void syncIssueCommentsOfAllIssues(List<GHIssue> ghIssues) {
        ghIssues.stream().forEach(ghIssue -> syncIssueCommentsOfIssue(ghIssue));
    }

    /**
     * Syncs issue comments from a specific GitHub issue.
     *
     * @param ghIssue The GitHub issue to sync the comments of.
     */
    public void syncIssueCommentsOfIssue(GHIssue ghIssue) {
        var builder = ghIssue.queryComments();
        builder.list().withPageSize(100).forEach(this::processIssueComment);
    }

    /**
     * Processes a single GitHub issue comment by updating or creating it in the
     * local repository.
     * Manages associations with issues and authors.
     *
     * @param ghIssueComment The GitHub issue comment to process.
     * @return The updated or newly created IssueComment entity, or {@code null} if
     *         an error occurred during update.
     */
    @Transactional
    public IssueComment processIssueComment(GHIssueComment ghIssueComment) {
        var result = issueCommentRepository
            .findById(ghIssueComment.getId())
            .map(issueComment -> {
                try {
                    if (
                        issueComment.getUpdatedAt() == null ||
                        issueComment.getUpdatedAt().isBefore(ghIssueComment.getUpdatedAt())
                    ) {
                        return issueCommentConverter.update(ghIssueComment, issueComment);
                    }
                    return issueComment;
                } catch (IOException e) {
                    logger.error("Failed to update issue comment {}: {}", ghIssueComment.getId(), e.getMessage());
                    return null;
                }
            })
            .orElseGet(() -> issueCommentConverter.convert(ghIssueComment));

        if (result == null) {
            return null;
        }

        // Link issue
        var ghIssue = ghIssueComment.getParent();
        var resultIssue = issueRepository
            .findById(ghIssue.getId())
            .orElseGet(() -> issueRepository.save(issueConverter.convert(ghIssue)));
        result.setIssue(resultIssue);

        // Link author
        try {
            var author = ghIssueComment.getUser();
            var resultAuthor = userSyncService.getOrCreateUser(author);
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for issue comment {}: {}", ghIssueComment.getId(), e.getMessage());
        }

        return issueCommentRepository.save(result);
    }
}
