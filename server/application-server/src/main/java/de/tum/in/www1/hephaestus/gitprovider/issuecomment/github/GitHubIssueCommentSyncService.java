package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import java.io.IOException;

import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueConverter;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubIssueCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentSyncService.class);

    private final GitHub github;
    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final GitHubIssueCommentConverter issueCommentConverter;
    private final GitHubIssueConverter issueConverter;
    private final GitHubUserConverter userConverter;

    public GitHubIssueCommentSyncService(
            GitHub github,
            IssueCommentRepository issueCommentRepository,
            IssueRepository issueRepository,
            UserRepository userRepository,
            GitHubIssueCommentConverter issueCommentConverter,
            GitHubIssueConverter issueConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.issueCommentRepository = issueCommentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.issueCommentConverter = issueCommentConverter;
        this.issueConverter = issueConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public IssueComment processIssueComment(GHIssueComment ghIssueComment) {
        var result = issueCommentRepository.findById(ghIssueComment.getId())
                .map(issueComment -> {
                    try {
                        if (issueComment.getUpdatedAt()
                                .isBefore(
                                        DateUtil.convertToOffsetDateTime(ghIssueComment.getUpdatedAt()))) {
                            return issueCommentConverter.update(ghIssueComment,
                                    issueComment);
                        }
                        return issueComment;
                    } catch (IOException e) {
                        logger.error("Failed to update issue comment {}: {}",
                                ghIssueComment.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> issueCommentConverter.convert(ghIssueComment));

        if (result == null) {
            return null;
        }

        // Link issue
        var ghIssue = ghIssueComment.getParent();
        var resultIssue = issueRepository.findById(ghIssue.getId())
                .orElseGet(() -> issueRepository.save(issueConverter.convert(ghIssue)));
        result.setIssue(resultIssue);

        // Link author
        try {
            var author = ghIssue.getUser();
            var resultAuthor = userRepository.findById(author.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(author)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for issue comment {}: {}", ghIssueComment.getId(), e.getMessage());
        }

        return issueCommentRepository.save(result);
    }
}