package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategory;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionCategoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified processor for GitHub discussions.
 * <p>
 * This service handles the conversion of GitHubDiscussionDTO to Discussion entities,
 * persists them, and manages related entities (categories, comments).
 * It's used by both the GraphQL sync service and webhook handlers.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubDiscussionProcessor extends BaseGitHubProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDiscussionProcessor.class);

    private final DiscussionRepository discussionRepository;
    private final DiscussionCategoryRepository categoryRepository;
    private final DiscussionCommentRepository commentRepository;

    public GitHubDiscussionProcessor(
        DiscussionRepository discussionRepository,
        DiscussionCategoryRepository categoryRepository,
        DiscussionCommentRepository commentRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.discussionRepository = discussionRepository;
        this.categoryRepository = categoryRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Process a GitHub discussion DTO and persist it as a Discussion entity.
     */
    @Transactional
    public Discussion process(GitHubDiscussionDTO dto, ProcessingContext context) {
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            logger.warn("Discussion DTO missing databaseId, skipping");
            return null;
        }

        Repository repository = context.repository();
        Optional<Discussion> existingOpt = discussionRepository.findById(dbId);

        Discussion discussion;
        boolean isNew = existingOpt.isEmpty();

        if (isNew) {
            discussion = createDiscussion(dto, repository);
            discussion = discussionRepository.save(discussion);
            logger.debug(
                "Created discussion #{} in {}",
                dto.number(),
                sanitizeForLog(context.repository().getNameWithOwner())
            );
        } else {
            discussion = existingOpt.get();
            Set<String> changedFields = updateDiscussion(dto, discussion, repository);
            discussion = discussionRepository.save(discussion);

            if (!changedFields.isEmpty()) {
                logger.debug(
                    "Updated discussion #{} in {} - changed: {}",
                    dto.number(),
                    sanitizeForLog(context.repository().getNameWithOwner()),
                    changedFields
                );
            }
        }

        // Process comments if present
        if (dto.comments() != null && !dto.comments().isEmpty()) {
            processComments(dto, discussion);
        }

        // Update answer reference
        if (dto.answer() != null) {
            updateAnswer(dto, discussion);
        }

        discussion.setLastSyncAt(Instant.now());
        return discussionRepository.save(discussion);
    }

    /**
     * Process a deleted discussion.
     */
    @Transactional
    public void processDeleted(GitHubDiscussionDTO dto, ProcessingContext context) {
        Long dbId = dto.getDatabaseId();
        if (dbId != null) {
            discussionRepository.deleteById(dbId);
            logger.info("Deleted discussion with id {}", dbId);
        }
    }

    // ==================== Entity Creation ====================

    private Discussion createDiscussion(GitHubDiscussionDTO dto, Repository repository) {
        Discussion discussion = new Discussion();
        discussion.setId(dto.getDatabaseId());
        discussion.setNumber(dto.number());
        discussion.setTitle(sanitize(dto.title()));
        discussion.setBody(sanitize(dto.body()));
        discussion.setState(convertState(dto.state()));
        discussion.setStateReason(convertStateReason(dto.stateReason()));
        discussion.setHtmlUrl(dto.htmlUrl());
        discussion.setLocked(dto.locked());
        discussion.setActiveLockReason(convertLockReason(dto.activeLockReason()));
        discussion.setCommentCount(dto.commentsCount());
        discussion.setCreatedAt(dto.createdAt());
        discussion.setUpdatedAt(dto.updatedAt());
        discussion.setClosedAt(dto.closedAt());
        discussion.setAnswerChosenAt(dto.answerChosenAt());
        discussion.setRepository(repository);

        // Author
        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            discussion.setAuthor(author);
        }

        // Answer chosen by
        if (dto.answerChosenBy() != null) {
            User answerChosenBy = findOrCreateUser(dto.answerChosenBy());
            discussion.setAnswerChosenBy(answerChosenBy);
        }

        // Category
        if (dto.category() != null) {
            DiscussionCategory category = findOrCreateCategory(dto.category(), repository);
            discussion.setCategory(category);
        }

        // Labels
        if (dto.labels() != null) {
            for (GitHubLabelDTO labelDto : dto.labels()) {
                Label label = findOrCreateLabel(labelDto, repository);
                if (label != null) {
                    discussion.getLabels().add(label);
                }
            }
        }

        return discussion;
    }

    // ==================== Entity Update ====================

    private Set<String> updateDiscussion(GitHubDiscussionDTO dto, Discussion discussion, Repository repository) {
        Set<String> changedFields = new HashSet<>();

        // Title
        if (!Objects.equals(discussion.getTitle(), dto.title())) {
            discussion.setTitle(sanitize(dto.title()));
            changedFields.add("title");
        }

        // Body
        if (!Objects.equals(discussion.getBody(), dto.body())) {
            discussion.setBody(sanitize(dto.body()));
            changedFields.add("body");
        }

        // State
        Discussion.State newState = convertState(dto.state());
        if (discussion.getState() != newState) {
            discussion.setState(newState);
            changedFields.add("state");
        }

        // State reason
        Discussion.StateReason newStateReason = convertStateReason(dto.stateReason());
        if (!Objects.equals(discussion.getStateReason(), newStateReason)) {
            discussion.setStateReason(newStateReason);
            changedFields.add("stateReason");
        }

        // Locked
        if (discussion.isLocked() != dto.locked()) {
            discussion.setLocked(dto.locked());
            changedFields.add("locked");
        }

        // Lock reason
        Discussion.LockReason newLockReason = convertLockReason(dto.activeLockReason());
        if (!Objects.equals(discussion.getActiveLockReason(), newLockReason)) {
            discussion.setActiveLockReason(newLockReason);
            changedFields.add("activeLockReason");
        }

        // Comment count
        if (discussion.getCommentCount() != dto.commentsCount()) {
            discussion.setCommentCount(dto.commentsCount());
            changedFields.add("commentCount");
        }

        // Timestamps
        if (!Objects.equals(discussion.getUpdatedAt(), dto.updatedAt())) {
            discussion.setUpdatedAt(dto.updatedAt());
        }
        if (!Objects.equals(discussion.getClosedAt(), dto.closedAt())) {
            discussion.setClosedAt(dto.closedAt());
            changedFields.add("closedAt");
        }
        if (!Objects.equals(discussion.getAnswerChosenAt(), dto.answerChosenAt())) {
            discussion.setAnswerChosenAt(dto.answerChosenAt());
            changedFields.add("answerChosenAt");
        }

        // Category
        if (dto.category() != null) {
            DiscussionCategory category = findOrCreateCategory(dto.category(), repository);
            if (!Objects.equals(discussion.getCategory(), category)) {
                discussion.setCategory(category);
                changedFields.add("category");
            }
        }

        return changedFields;
    }

    // ==================== Helper Methods ====================

    private void processComments(GitHubDiscussionDTO dto, Discussion discussion) {
        for (GitHubDiscussionCommentDTO commentDto : dto.comments()) {
            processComment(commentDto, discussion, null);
        }
    }

    private DiscussionComment processComment(
        GitHubDiscussionCommentDTO dto,
        Discussion discussion,
        @Nullable DiscussionComment parent
    ) {
        if (dto == null || dto.getDatabaseId() == null) {
            return null;
        }

        Long commentId = dto.getDatabaseId();
        Optional<DiscussionComment> existingOpt = commentRepository.findById(commentId);

        DiscussionComment comment;
        if (existingOpt.isEmpty()) {
            comment = new DiscussionComment();
            comment.setId(commentId);
            comment.setBody(sanitize(dto.body()));
            comment.setAnswer(dto.isAnswer());
            comment.setMinimized(dto.isMinimized());
            comment.setMinimizedReason(dto.minimizedReason());
            comment.setAuthorAssociation(convertAuthorAssociation(dto.authorAssociation()));
            comment.setCreatedAt(dto.createdAt());
            comment.setUpdatedAt(dto.updatedAt());
            comment.setDiscussion(discussion);
            comment.setParentComment(parent);

            if (dto.author() != null) {
                User author = findOrCreateUser(dto.author());
                comment.setAuthor(author);
            }

            comment = commentRepository.save(comment);
        } else {
            comment = existingOpt.get();
            comment.setBody(sanitize(dto.body()));
            comment.setAnswer(dto.isAnswer());
            comment.setMinimized(dto.isMinimized());
            comment.setMinimizedReason(dto.minimizedReason());
            comment.setUpdatedAt(dto.updatedAt());
            comment = commentRepository.save(comment);
        }

        // Process replies
        if (dto.replies() != null) {
            for (GitHubDiscussionCommentDTO replyDto : dto.replies()) {
                processComment(replyDto, discussion, comment);
            }
        }

        comment.setLastSyncAt(Instant.now());
        return commentRepository.save(comment);
    }

    private void updateAnswer(GitHubDiscussionDTO dto, Discussion discussion) {
        if (dto.answer() != null && dto.answer().getDatabaseId() != null) {
            Optional<DiscussionComment> answerOpt = commentRepository.findById(dto.answer().getDatabaseId());
            answerOpt.ifPresent(discussion::setAnswerComment);
        }
    }

    @Nullable
    private DiscussionCategory findOrCreateCategory(GitHubDiscussionCategoryDTO dto, Repository repository) {
        if (dto == null || dto.nodeId() == null) {
            return null;
        }
        return categoryRepository
            .findById(dto.nodeId())
            .orElseGet(() -> {
                DiscussionCategory category = new DiscussionCategory();
                category.setId(dto.nodeId());
                category.setName(dto.name());
                category.setSlug(dto.slug());
                category.setEmoji(dto.emoji());
                category.setDescription(dto.description());
                category.setAnswerable(Boolean.TRUE.equals(dto.isAnswerable()));
                category.setCreatedAt(dto.createdAt());
                category.setRepository(repository);
                return categoryRepository.save(category);
            });
    }

    // ==================== Conversion Helpers ====================

    private Discussion.State convertState(@Nullable String state) {
        if (state == null) {
            return Discussion.State.OPEN;
        }
        return "closed".equalsIgnoreCase(state) ? Discussion.State.CLOSED : Discussion.State.OPEN;
    }

    @Nullable
    private Discussion.StateReason convertStateReason(@Nullable String stateReason) {
        if (stateReason == null) {
            return null;
        }
        return switch (stateReason.toUpperCase()) {
            case "RESOLVED" -> Discussion.StateReason.RESOLVED;
            case "OUTDATED" -> Discussion.StateReason.OUTDATED;
            case "DUPLICATE" -> Discussion.StateReason.DUPLICATE;
            default -> Discussion.StateReason.UNKNOWN;
        };
    }

    @Nullable
    private Discussion.LockReason convertLockReason(@Nullable String lockReason) {
        if (lockReason == null) {
            return null;
        }
        return switch (lockReason.toUpperCase()) {
            case "OFF_TOPIC" -> Discussion.LockReason.OFF_TOPIC;
            case "RESOLVED" -> Discussion.LockReason.RESOLVED;
            case "SPAM" -> Discussion.LockReason.SPAM;
            case "TOO_HEATED" -> Discussion.LockReason.TOO_HEATED;
            default -> null;
        };
    }

    @Nullable
    private DiscussionComment.AuthorAssociation convertAuthorAssociation(@Nullable String association) {
        if (association == null) {
            return null;
        }
        return switch (association.toUpperCase()) {
            case "MEMBER" -> DiscussionComment.AuthorAssociation.MEMBER;
            case "OWNER" -> DiscussionComment.AuthorAssociation.OWNER;
            case "COLLABORATOR" -> DiscussionComment.AuthorAssociation.COLLABORATOR;
            case "CONTRIBUTOR" -> DiscussionComment.AuthorAssociation.CONTRIBUTOR;
            case "FIRST_TIME_CONTRIBUTOR" -> DiscussionComment.AuthorAssociation.FIRST_TIME_CONTRIBUTOR;
            case "FIRST_TIMER" -> DiscussionComment.AuthorAssociation.FIRST_TIMER;
            case "MANNEQUIN" -> DiscussionComment.AuthorAssociation.MANNEQUIN;
            default -> DiscussionComment.AuthorAssociation.NONE;
        };
    }
}
