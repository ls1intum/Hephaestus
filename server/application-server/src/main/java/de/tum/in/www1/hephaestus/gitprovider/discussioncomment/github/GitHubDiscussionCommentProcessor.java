package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub Discussion Comments.
 * <p>
 * This service handles the conversion of GitHubDiscussionCommentDTO to DiscussionComment entities.
 * <p>
 * Discussion comments can be top-level comments or replies to other comments.
 * Reply threading is handled via the parentComment relationship, resolved by node ID.
 */
@Slf4j
@Service
public class GitHubDiscussionCommentProcessor extends BaseGitHubProcessor {

    private final DiscussionCommentRepository commentRepository;

    public GitHubDiscussionCommentProcessor(
        UserRepository userRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        GitHubUserProcessor gitHubUserProcessor,
        DiscussionCommentRepository commentRepository
    ) {
        super(userRepository, labelRepository, milestoneRepository, gitHubUserProcessor);
        this.commentRepository = commentRepository;
    }

    /**
     * Process a GitHub discussion comment DTO and persist it as a DiscussionComment entity.
     *
     * @param dto        the discussion comment DTO
     * @param discussion the parent discussion entity
     * @param context    the processing context
     * @return the created or updated DiscussionComment entity, or null if processing failed
     */
    @Transactional
    public DiscussionComment process(GitHubDiscussionCommentDTO dto, Discussion discussion, ProcessingContext context) {
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped discussion comment processing: reason=missingDatabaseId");
            return null;
        }

        // Check if this is an update
        Optional<DiscussionComment> existingOpt = commentRepository.findById(dbId);
        boolean isNew = existingOpt.isEmpty();

        // Resolve related entities
        User author = dto.author() != null ? findOrCreateUser(dto.author()) : null;

        // Get or create the comment
        DiscussionComment comment = existingOpt.orElseGet(() -> {
            DiscussionComment c = new DiscussionComment();
            c.setId(dbId);
            return c;
        });

        // Update fields
        comment.setBody(sanitize(dto.body()));
        comment.setHtmlUrl(dto.htmlUrl());
        comment.setAnswer(dto.isAnswer());
        comment.setMinimized(dto.isMinimized());
        comment.setMinimizedReason(dto.minimizedReason());
        comment.setAuthorAssociation(convertAuthorAssociation(dto.authorAssociation()));
        comment.setCreatedAt(dto.createdAt());
        comment.setUpdatedAt(dto.updatedAt());
        comment.setLastSyncAt(Instant.now());

        comment.setDiscussion(discussion);
        comment.setAuthor(author);

        // Note: Parent comment resolution is handled in a second pass
        // since the parent may not exist yet during initial sync

        // Save the comment
        comment = commentRepository.save(comment);

        if (isNew) {
            log.debug("Created discussion comment: commentId={}", dbId);
        } else {
            log.debug("Updated discussion comment: commentId={}", dbId);
        }

        return comment;
    }

    /**
     * Delete a discussion comment by its database ID.
     * <p>
     * This method is the single entry point for discussion comment deletion,
     * following the pattern where all data mutations are handled by processors.
     *
     * @param commentDto the comment DTO containing the ID to delete
     */
    @Transactional
    public void processDeleted(GitHubDiscussionCommentDTO commentDto) {
        Long dbId = commentDto.getDatabaseId();
        if (dbId != null) {
            commentRepository.deleteById(dbId);
            log.info("Deleted discussion comment: commentId={}", dbId);
        } else {
            log.warn("Cannot delete discussion comment: reason=missingDatabaseId");
        }
    }

    /**
     * Resolve reply threading after all comments have been created.
     * <p>
     * This method should be called after processing all comments to establish
     * parent-child relationships based on the replyToNodeId.
     *
     * @param comment       the comment to update
     * @param parentComment the parent comment (if this is a reply)
     */
    @Transactional
    public void resolveParentComment(DiscussionComment comment, @Nullable DiscussionComment parentComment) {
        if (parentComment != null) {
            comment.setParentComment(parentComment);
            commentRepository.save(comment);
            log.debug("Resolved parent comment: commentId={}, parentId={}", comment.getId(), parentComment.getId());
        }
    }

    /**
     * Convert author association string to AuthorAssociation enum.
     */
    @Nullable
    private AuthorAssociation convertAuthorAssociation(@Nullable String association) {
        if (association == null) {
            return null;
        }
        return switch (association.toUpperCase()) {
            case "COLLABORATOR" -> AuthorAssociation.COLLABORATOR;
            case "CONTRIBUTOR" -> AuthorAssociation.CONTRIBUTOR;
            case "FIRST_TIME_CONTRIBUTOR" -> AuthorAssociation.FIRST_TIME_CONTRIBUTOR;
            case "FIRST_TIMER" -> AuthorAssociation.FIRST_TIMER;
            case "MANNEQUIN" -> AuthorAssociation.MANNEQUIN;
            case "MEMBER" -> AuthorAssociation.MEMBER;
            case "OWNER" -> AuthorAssociation.OWNER;
            case "NONE" -> AuthorAssociation.NONE;
            default -> AuthorAssociation.NONE;
        };
    }
}
