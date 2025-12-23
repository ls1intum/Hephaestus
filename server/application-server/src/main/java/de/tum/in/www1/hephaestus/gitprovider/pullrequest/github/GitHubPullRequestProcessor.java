package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EntityEvents;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified processor for GitHub pull requests.
 * <p>
 * This service handles the conversion of GitHubPullRequestDTO to PullRequest
 * entities,
 * persists them, and publishes appropriate domain events. It's used by both
 * the GraphQL sync service and webhook handlers.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Domain events published for reactive feature development</li>
 * <li>No hub4j types - works exclusively with DTOs</li>
 * </ul>
 */
@Service
public class GitHubPullRequestProcessor extends BaseGitHubProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestProcessor.class);

    private final PullRequestRepository pullRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestProcessor(
        PullRequestRepository pullRequestRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.pullRequestRepository = pullRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub pull request DTO and persist it as a PullRequest entity.
     * Publishes appropriate domain events based on what changed.
     *
     * @return the processed PullRequest, or null if the DTO has no valid ID
     */
    @Transactional
    public PullRequest process(GitHubPullRequestDTO dto, ProcessingContext context) {
        Long prId = dto.getDatabaseId();
        if (prId == null) {
            logger.warn("Cannot process pull request with null ID");
            return null;
        }
        Optional<PullRequest> existingOpt = pullRequestRepository.findById(prId);

        PullRequest pr;
        boolean isNew = existingOpt.isEmpty();

        if (isNew) {
            pr = createPullRequest(dto, context.repository());
            pr = pullRequestRepository.save(pr);
            eventPublisher.publishEvent(new EntityEvents.Created<>(pr, context));
            logger.debug("Created PR #{} in {}", dto.number(), context.repository().getNameWithOwner());
        } else {
            pr = existingOpt.get();
            Set<String> changedFields = updatePullRequest(dto, pr, context.repository());
            pr = pullRequestRepository.save(pr);

            if (!changedFields.isEmpty()) {
                eventPublisher.publishEvent(new EntityEvents.Updated<>(pr, changedFields, context));
                logger.debug(
                    "Updated PR #{} in {} - changed: {}",
                    dto.number(),
                    context.repository().getNameWithOwner(),
                    changedFields
                );
            }
        }

        return pr;
    }

    /**
     * Process a closed event.
     */
    @Transactional
    public PullRequest processClosed(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        boolean wasMerged = dto.isMerged();

        eventPublisher.publishEvent(new EntityEvents.Closed<>(pr, wasMerged ? "merged" : "closed", context));

        if (wasMerged) {
            eventPublisher.publishEvent(new EntityEvents.PullRequestMerged(pr, context));
            logger.info("PR #{} merged", pr.getNumber());
        } else {
            logger.debug("PR #{} closed without merging", pr.getNumber());
        }

        return pr;
    }

    /**
     * Process a ready_for_review event.
     */
    @Transactional
    public PullRequest processReadyForReview(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(new EntityEvents.PullRequestReady(pr, context));
        logger.info("PR #{} is ready for review", pr.getNumber());
        return pr;
    }

    /**
     * Process a converted_to_draft event.
     */
    @Transactional
    public PullRequest processConvertedToDraft(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(new EntityEvents.PullRequestDrafted(pr, context));
        logger.debug("PR #{} converted to draft", pr.getNumber());
        return pr;
    }

    /**
     * Process a synchronize event (new commits pushed).
     */
    @Transactional
    public PullRequest processSynchronize(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(new EntityEvents.PullRequestSynchronized(pr, context));
        logger.debug("PR #{} synchronized (new commits)", pr.getNumber());
        return pr;
    }

    /**
     * Process a labeled event.
     */
    @Transactional
    public PullRequest processLabeled(GitHubPullRequestDTO dto, GitHubLabelDTO labelDto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(new EntityEvents.Labeled<>(pr, label, context));
            logger.debug("PR #{} labeled: {}", pr.getNumber(), label.getName());
        }
        return pr;
    }

    /**
     * Process an unlabeled event.
     */
    @Transactional
    public PullRequest processUnlabeled(GitHubPullRequestDTO dto, GitHubLabelDTO labelDto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(new EntityEvents.Unlabeled<>(pr, label, context));
            logger.debug("PR #{} unlabeled: {}", pr.getNumber(), label.getName());
        }
        return pr;
    }

    // ==================== Private helper methods ====================

    private PullRequest createPullRequest(GitHubPullRequestDTO dto, Repository repository) {
        PullRequest pr = new PullRequest();
        pr.setId(dto.getDatabaseId());
        pr.setNumber(dto.number());
        pr.setTitle(sanitize(dto.title()));
        pr.setBody(sanitize(dto.body()));
        pr.setState(convertState(dto.state()));
        pr.setHtmlUrl(dto.htmlUrl());
        pr.setCreatedAt(dto.createdAt());
        pr.setUpdatedAt(dto.updatedAt());
        pr.setClosedAt(dto.closedAt());
        pr.setMergedAt(dto.mergedAt());
        pr.setDraft(dto.isDraft());
        pr.setMerged(dto.isMerged());
        pr.setAdditions(dto.additions());
        pr.setDeletions(dto.deletions());
        pr.setChangedFiles(dto.changedFiles());
        pr.setCommits(dto.commits());
        pr.setCommentsCount(dto.commentsCount());
        pr.setRepository(repository);
        pr.setHasPullRequest(true); // It is a PR

        // Link author
        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            pr.setAuthor(author);
        }

        // Link assignees
        if (dto.assignees() != null) {
            Set<User> assignees = new HashSet<>();
            for (GitHubUserDTO assigneeDto : dto.assignees()) {
                User assignee = findOrCreateUser(assigneeDto);
                if (assignee != null) {
                    assignees.add(assignee);
                }
            }
            pr.setAssignees(assignees);
        }

        // Link requested reviewers
        if (dto.requestedReviewers() != null) {
            Set<User> reviewers = new HashSet<>();
            for (GitHubUserDTO reviewerDto : dto.requestedReviewers()) {
                User reviewer = findOrCreateUser(reviewerDto);
                if (reviewer != null) {
                    reviewers.add(reviewer);
                }
            }
            pr.setRequestedReviewers(reviewers);
        }

        // Link labels
        if (dto.labels() != null) {
            Set<Label> labels = new HashSet<>();
            for (GitHubLabelDTO labelDto : dto.labels()) {
                Label label = findOrCreateLabel(labelDto, repository);
                if (label != null) {
                    labels.add(label);
                }
            }
            pr.setLabels(labels);
        }

        // Link milestone
        if (dto.milestone() != null) {
            Milestone milestone = findOrCreateMilestone(dto.milestone(), repository);
            pr.setMilestone(milestone);
        }

        return pr;
    }

    private Set<String> updatePullRequest(GitHubPullRequestDTO dto, PullRequest pr, Repository repository) {
        Set<String> changedFields = new HashSet<>();

        // Only update if newer
        if (pr.getUpdatedAt() != null && dto.updatedAt() != null && !dto.updatedAt().isAfter(pr.getUpdatedAt())) {
            return changedFields;
        }

        if (!Objects.equals(pr.getTitle(), sanitize(dto.title()))) {
            pr.setTitle(sanitize(dto.title()));
            changedFields.add("title");
        }

        if (!Objects.equals(pr.getBody(), sanitize(dto.body()))) {
            pr.setBody(sanitize(dto.body()));
            changedFields.add("body");
        }

        var newState = convertState(dto.state());
        if (pr.getState() != newState) {
            pr.setState(newState);
            changedFields.add("state");
        }

        if (pr.isDraft() != dto.isDraft()) {
            pr.setDraft(dto.isDraft());
            changedFields.add("draft");
        }

        if (pr.isMerged() != dto.isMerged()) {
            pr.setMerged(dto.isMerged());
            changedFields.add("merged");
        }

        if (!Objects.equals(pr.getMergedAt(), dto.mergedAt())) {
            pr.setMergedAt(dto.mergedAt());
            changedFields.add("mergedAt");
        }

        if (pr.getAdditions() != dto.additions()) {
            pr.setAdditions(dto.additions());
            changedFields.add("additions");
        }

        if (pr.getDeletions() != dto.deletions()) {
            pr.setDeletions(dto.deletions());
            changedFields.add("deletions");
        }

        if (pr.getCommits() != dto.commits()) {
            pr.setCommits(dto.commits());
            changedFields.add("commits");
        }

        pr.setUpdatedAt(dto.updatedAt());

        // Update labels
        if (dto.labels() != null) {
            Set<Label> newLabels = new HashSet<>();
            for (GitHubLabelDTO labelDto : dto.labels()) {
                Label label = findOrCreateLabel(labelDto, repository);
                if (label != null) {
                    newLabels.add(label);
                }
            }
            if (!pr.getLabels().equals(newLabels)) {
                pr.getLabels().clear();
                pr.getLabels().addAll(newLabels);
                changedFields.add("labels");
            }
        }

        // Update requested reviewers
        if (dto.requestedReviewers() != null) {
            Set<User> newReviewers = new HashSet<>();
            for (GitHubUserDTO reviewerDto : dto.requestedReviewers()) {
                User reviewer = findOrCreateUser(reviewerDto);
                if (reviewer != null) {
                    newReviewers.add(reviewer);
                }
            }
            if (!pr.getRequestedReviewers().equals(newReviewers)) {
                pr.getRequestedReviewers().clear();
                pr.getRequestedReviewers().addAll(newReviewers);
                changedFields.add("requestedReviewers");
            }
        }

        return changedFields;
    }

    private de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State convertState(String state) {
        if (state == null) {
            return de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
            case "CLOSED" -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.CLOSED;
            default -> de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN;
        };
    }
}
