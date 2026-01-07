package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub issue comments.
 * <p>
 * This service handles the conversion of GitHubCommentDTO to IssueComment entities,
 * persists them, and publishes appropriate domain events.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Domain events published for reactive feature development</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubIssueCommentProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueCommentProcessor.class);

    private final IssueCommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubIssueCommentProcessor(
        IssueCommentRepository commentRepository,
        IssueRepository issueRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub comment DTO and persist it as an IssueComment entity.
     * Publishes appropriate domain events based on what changed.
     *
     * @param dto the GitHub comment DTO
     * @param issueId the database ID of the issue this comment belongs to
     * @param context processing context with workspace information
     * @return the persisted IssueComment entity, or null if processing failed
     */
    @Transactional
    public IssueComment process(GitHubCommentDTO dto, Long issueId, ProcessingContext context) {
        if (dto == null || dto.id() == null) {
            log.warn("Comment DTO is null or missing ID, skipping");
            return null;
        }

        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            log.warn("Issue not found for comment: issueId={}", issueId);
            return null;
        }

        boolean isNew = !commentRepository.existsById(dto.id());

        IssueComment comment = commentRepository.findById(dto.id()).orElseGet(IssueComment::new);
        Set<String> changedFields = new HashSet<>();

        // Set ID for new comments
        if (isNew) {
            comment.setId(dto.id());
        }

        // Update body if changed
        if (dto.body() != null && !dto.body().equals(comment.getBody())) {
            changedFields.add("body");
            comment.setBody(dto.body());
        }

        // Set html URL (only on create typically)
        if (dto.htmlUrl() != null && !dto.htmlUrl().equals(comment.getHtmlUrl())) {
            changedFields.add("htmlUrl");
            comment.setHtmlUrl(dto.htmlUrl());
        }

        // Set timestamps
        if (dto.createdAt() != null) {
            comment.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            comment.setUpdatedAt(dto.updatedAt());
        }

        // Set author association
        AuthorAssociation authorAssociation = AuthorAssociation.fromString(dto.authorAssociation());
        if (!authorAssociation.equals(comment.getAuthorAssociation())) {
            changedFields.add("authorAssociation");
            comment.setAuthorAssociation(authorAssociation);
        }

        // Set issue relationship
        comment.setIssue(issue);

        // Link author if present and not already set
        if (dto.author() != null && dto.author().getDatabaseId() != null && comment.getAuthor() == null) {
            userRepository
                .findById(dto.author().getDatabaseId())
                .ifPresent(user -> {
                    comment.setAuthor(user);
                    changedFields.add("author");
                });
        }

        IssueComment saved = commentRepository.save(comment);

        // Publish domain events with DTOs (safe for async handlers)
        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.CommentCreated(
                    EventPayload.CommentData.from(saved),
                    issueId,
                    EventContext.from(context)
                )
            );
            log.debug("Created comment {} for issue {}", saved.getId(), issueId);
        } else if (!changedFields.isEmpty()) {
            eventPublisher.publishEvent(
                new DomainEvent.CommentUpdated(
                    EventPayload.CommentData.from(saved),
                    issueId,
                    changedFields,
                    EventContext.from(context)
                )
            );
            log.debug("Updated comment {} with changes: {}", saved.getId(), changedFields);
        }

        return saved;
    }

    /**
     * Delete an issue comment by ID.
     * Publishes a Deleted domain event.
     *
     * @param commentId the ID of the comment to delete
     * @param context processing context
     */
    @Transactional
    public void delete(Long commentId, ProcessingContext context) {
        if (commentId == null) {
            return;
        }

        commentRepository
            .findById(commentId)
            .ifPresent(comment -> {
                Long issueId = comment.getIssue() != null ? comment.getIssue().getId() : null;
                commentRepository.deleteById(commentId);
                eventPublisher.publishEvent(
                    new DomainEvent.CommentDeleted(commentId, issueId, EventContext.from(context))
                );
                log.info("Deleted comment {}", commentId);
            });
    }
}
