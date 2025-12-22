package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.GitHubIssueProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub issue_comment webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubIssueCommentMessageHandler extends GitHubMessageHandler<GitHubIssueCommentEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubIssueProcessor issueProcessor;
    private final IssueCommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    GitHubIssueCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubIssueProcessor issueProcessor,
        IssueCommentRepository commentRepository,
        IssueRepository issueRepository,
        UserRepository userRepository
    ) {
        super(GitHubIssueCommentEventDTO.class);
        this.contextFactory = contextFactory;
        this.issueProcessor = issueProcessor;
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected String getEventKey() {
        return "issue_comment";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubIssueCommentEventDTO event) {
        var commentDto = event.comment();
        var issueDto = event.issue();

        if (commentDto == null || issueDto == null) {
            logger.warn("Received issue_comment event with missing data");
            return;
        }

        logger.info(
            "Received issue_comment event: action={}, issue=#{}, comment={}, repo={}",
            event.action(),
            issueDto.number(),
            commentDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure issue exists
        issueProcessor.process(issueDto, context);

        // Handle comment action
        if ("deleted".equals(event.action())) {
            commentRepository.deleteById(commentDto.id());
        } else {
            processComment(commentDto, issueDto.getDatabaseId(), context);
        }
    }

    private IssueComment processComment(
        GitHubIssueCommentEventDTO.GitHubCommentDTO dto,
        Long issueId,
        ProcessingContext context
    ) {
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            logger.warn("Issue not found for comment: issueId={}", issueId);
            return null;
        }

        return commentRepository
            .findById(dto.id())
            .map(comment -> {
                comment.setBody(dto.body());
                comment.setUpdatedAt(dto.updatedAt());
                return commentRepository.save(comment);
            })
            .orElseGet(() -> {
                IssueComment comment = new IssueComment();
                comment.setId(dto.id());
                comment.setBody(dto.body());
                comment.setHtmlUrl(dto.htmlUrl());
                comment.setCreatedAt(dto.createdAt());
                comment.setUpdatedAt(dto.updatedAt());
                comment.setIssue(issue);

                // Link author if present
                if (dto.author() != null && dto.author().id() != null) {
                    userRepository.findById(dto.author().id()).ifPresent(comment::setAuthor);
                }

                return commentRepository.save(comment);
            });
    }
}
