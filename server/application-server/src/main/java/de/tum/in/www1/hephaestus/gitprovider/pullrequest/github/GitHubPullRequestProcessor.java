package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
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
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubPullRequestProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestProcessor.class);

    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final CommitRepository commitRepository;
    private final CommitAuthorResolver commitAuthorResolver;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestProcessor(
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        CommitRepository commitRepository,
        CommitAuthorResolver commitAuthorResolver,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository,
        GitHubUserProcessor gitHubUserProcessor,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository, gitHubUserProcessor);
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.commitRepository = commitRepository;
        this.commitAuthorResolver = commitAuthorResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub pull request DTO and persist it as a PullRequest entity.
     * Publishes appropriate domain events based on what changed.
     * <p>
     * Uses atomic upsert to prevent race conditions when concurrent threads
     * (e.g., multiple NATS consumers or webhook handlers) process the same PR.
     * <p>
     * Uses (repository_id, number) as the canonical lookup key to ensure idempotency
     * across both GraphQL sync and webhook events, which use different ID formats.
     *
     * @return the processed PullRequest, or null if the repository context is missing
     */
    @Transactional
    public PullRequest process(GitHubPullRequestDTO dto, ProcessingContext context) {
        Repository repository = context.repository();
        if (repository == null || repository.getId() == null) {
            log.warn("Skipped pull request processing: reason=missingRepository, prNumber={}", dto.number());
            return null;
        }

        // Check for valid database ID - required for assigned ID strategy
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped pull request processing: reason=missingDatabaseId, prNumber={}", dto.number());
            return null;
        }

        // Check if this is an update (for event publishing purposes)
        Optional<PullRequest> existingOpt = pullRequestRepository.findByRepositoryIdAndNumber(
            repository.getId(),
            dto.number()
        );
        boolean isNew = existingOpt.isEmpty();

        // Detect issue_type mismatch: entity exists as ISSUE but we're processing it as a PR.
        // The upsertCore native SQL will correct the discriminator, so we just log here.
        if (isNew) {
            Optional<Issue> existingIssue = issueRepository.findByRepositoryIdAndNumber(
                repository.getId(),
                dto.number()
            );
            if (existingIssue.isPresent()) {
                log.info(
                    "Updating issue_type from ISSUE to PULL_REQUEST: repositoryId={}, number={}",
                    repository.getId(),
                    dto.number()
                );
            }
        }

        // Skip update if existing data is newer (prevents stale webhooks from overwriting)
        if (!isNew) {
            PullRequest existing = existingOpt.get();
            if (
                existing.getUpdatedAt() != null &&
                dto.updatedAt() != null &&
                !dto.updatedAt().isAfter(existing.getUpdatedAt())
            ) {
                log.debug(
                    "Skipped stale PR update: prId={}, existingUpdatedAt={}, dtoUpdatedAt={}",
                    existing.getId(),
                    existing.getUpdatedAt(),
                    dto.updatedAt()
                );
                return existing;
            }
        }

        // Resolve related entities BEFORE the upsert
        User author = dto.author() != null ? findOrCreateUser(dto.author()) : null;
        User mergedBy = dto.mergedBy() != null ? findOrCreateUser(dto.mergedBy()) : null;
        Milestone milestone = dto.milestone() != null ? findOrCreateMilestone(dto.milestone(), repository) : null;

        // Extract branch info
        String headRefName = dto.head() != null ? dto.head().ref() : null;
        String headRefOid = dto.head() != null ? dto.head().sha() : null;
        String baseRefName = dto.base() != null ? dto.base().ref() : null;
        String baseRefOid = dto.base() != null ? dto.base().sha() : null;

        // Use atomic upsert to handle concurrent inserts
        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            dbId,
            dto.number(),
            sanitize(dto.title()),
            sanitize(dto.body()),
            convertState(dto.state()).name(),
            null, // stateReason not used for PRs
            dto.htmlUrl(),
            dto.locked(),
            dto.closedAt(),
            dto.commentsCount(),
            now,
            dto.createdAt(),
            dto.updatedAt(),
            author != null ? author.getId() : null,
            repository.getId(),
            milestone != null ? milestone.getId() : null,
            dto.mergedAt(),
            dto.isDraft(),
            dto.isMerged(),
            dto.commits(),
            dto.additions(),
            dto.deletions(),
            dto.changedFiles(),
            dto.reviewDecision() != null ? dto.reviewDecision().name() : null,
            dto.mergeStateStatus() != null ? dto.mergeStateStatus().name() : null,
            dto.isMergeable(),
            headRefName,
            baseRefName,
            headRefOid,
            baseRefOid,
            mergedBy != null ? mergedBy.getId() : null
        );

        // Fetch the PR to get a managed entity and handle relationships
        PullRequest pr = pullRequestRepository
            .findByRepositoryIdAndNumber(repository.getId(), dto.number())
            .orElseThrow(() ->
                new IllegalStateException(
                    "PullRequest not found after upsert: repositoryId=" +
                        repository.getId() +
                        ", number=" +
                        dto.number()
                )
            );

        // Handle ManyToMany relationships (labels, assignees, requestedReviewers)
        boolean relationshipsChanged = updateRelationships(dto, pr, repository);

        // Save relationship changes
        if (relationshipsChanged) {
            pr = pullRequestRepository.save(pr);
        }

        // Upsert merge commit if present (from GraphQL data — zero extra rate limit cost)
        upsertMergeCommit(dto, repository);

        // Publish events
        if (isNew) {
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestCreated(EventPayload.PullRequestData.from(pr), EventContext.from(context))
            );
            log.debug("Created pull request: prId={}, prNumber={}", pr.getId(), dto.number());
        } else {
            Set<String> changedFields = computeChangedFields(existingOpt.get(), pr);
            if (!changedFields.isEmpty() || relationshipsChanged) {
                if (relationshipsChanged) {
                    changedFields.add("relationships");
                }
                eventPublisher.publishEvent(
                    new DomainEvent.PullRequestUpdated(
                        EventPayload.PullRequestData.from(pr),
                        changedFields,
                        EventContext.from(context)
                    )
                );
                log.debug("Updated pull request: prId={}, changedFields={}", pr.getId(), changedFields);
            }
        }

        return pr;
    }

    /**
     * Updates ManyToMany relationships that can't be handled by the atomic upsert.
     *
     * @return true if any relationships were changed
     */
    private boolean updateRelationships(GitHubPullRequestDTO dto, PullRequest pr, Repository repository) {
        boolean assigneesChanged = updateAssignees(dto.assignees(), pr.getAssignees());
        boolean labelsChanged = updateLabels(dto.labels(), pr.getLabels(), repository);
        boolean reviewersChanged = updateRequestedReviewers(dto.requestedReviewers(), pr.getRequestedReviewers());
        return assigneesChanged || labelsChanged || reviewersChanged;
    }

    /**
     * Computes which fields changed between the old and new PR state.
     */
    private Set<String> computeChangedFields(PullRequest oldPr, PullRequest newPr) {
        Set<String> changedFields = new HashSet<>();

        if (!Objects.equals(oldPr.getTitle(), newPr.getTitle())) {
            changedFields.add("title");
        }
        if (!Objects.equals(oldPr.getBody(), newPr.getBody())) {
            changedFields.add("body");
        }
        if (oldPr.getState() != newPr.getState()) {
            changedFields.add("state");
        }
        if (oldPr.isDraft() != newPr.isDraft()) {
            changedFields.add("draft");
        }
        if (oldPr.isMerged() != newPr.isMerged()) {
            changedFields.add("merged");
        }
        if (!Objects.equals(oldPr.getMergedAt(), newPr.getMergedAt())) {
            changedFields.add("mergedAt");
        }
        if (oldPr.getAdditions() != newPr.getAdditions()) {
            changedFields.add("additions");
        }
        if (oldPr.getDeletions() != newPr.getDeletions()) {
            changedFields.add("deletions");
        }
        if (oldPr.getCommits() != newPr.getCommits()) {
            changedFields.add("commits");
        }
        if (!Objects.equals(oldPr.getHeadRefOid(), newPr.getHeadRefOid())) {
            changedFields.add("headRefOid");
        }

        return changedFields;
    }

    /**
     * Process a closed event.
     */
    @Transactional
    public PullRequest processClosed(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        boolean wasMerged = dto.isMerged();
        EventPayload.PullRequestData prData = EventPayload.PullRequestData.from(pr);
        EventContext eventContext = EventContext.from(context);

        eventPublisher.publishEvent(new DomainEvent.PullRequestClosed(prData, wasMerged, eventContext));

        if (wasMerged) {
            eventPublisher.publishEvent(new DomainEvent.PullRequestMerged(prData, eventContext));
            log.info("Merged pull request: prId={}, prNumber={}", pr.getId(), pr.getNumber());
        } else {
            log.debug("Closed pull request: prId={}, merged=false", pr.getId());
        }

        return pr;
    }

    /**
     * Process a reopened event.
     */
    @Transactional
    public PullRequest processReopened(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(
            new DomainEvent.PullRequestReopened(EventPayload.PullRequestData.from(pr), EventContext.from(context))
        );
        log.debug("Reopened pull request: prId={}", pr.getId());
        return pr;
    }

    /**
     * Process a ready_for_review event.
     */
    @Transactional
    public PullRequest processReadyForReview(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(
            new DomainEvent.PullRequestReady(EventPayload.PullRequestData.from(pr), EventContext.from(context))
        );
        log.debug("Marked pull request ready for review: prId={}", pr.getId());
        return pr;
    }

    /**
     * Process a converted_to_draft event.
     */
    @Transactional
    public PullRequest processConvertedToDraft(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(
            new DomainEvent.PullRequestDrafted(EventPayload.PullRequestData.from(pr), EventContext.from(context))
        );
        log.debug("Converted pull request to draft: prId={}", pr.getId());
        return pr;
    }

    /**
     * Process a synchronize event (new commits pushed).
     */
    @Transactional
    public PullRequest processSynchronize(GitHubPullRequestDTO dto, ProcessingContext context) {
        PullRequest pr = process(dto, context);
        eventPublisher.publishEvent(
            new DomainEvent.PullRequestSynchronized(EventPayload.PullRequestData.from(pr), EventContext.from(context))
        );
        log.debug("Synchronized pull request: prId={}", pr.getId());
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
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestLabeled(
                    EventPayload.PullRequestData.from(pr),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            log.debug("Labeled pull request: prId={}, labelName={}", pr.getId(), label.getName());
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
            eventPublisher.publishEvent(
                new DomainEvent.PullRequestUnlabeled(
                    EventPayload.PullRequestData.from(pr),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            log.debug("Unlabeled pull request: prId={}, labelName={}", pr.getId(), label.getName());
        }
        return pr;
    }

    private Issue.State convertState(String state) {
        if (state == null) {
            log.warn(
                "PR state is null, defaulting to OPEN. This may indicate missing data in webhook or GraphQL response."
            );
            return Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "OPEN" -> Issue.State.OPEN;
            case "CLOSED" -> Issue.State.CLOSED;
            case "MERGED" -> Issue.State.MERGED;
            default -> {
                log.warn("Unknown PR state '{}', defaulting to OPEN", state);
                yield Issue.State.OPEN;
            }
        };
    }

    /**
     * Upserts the merge commit when GraphQL data provides full merge commit metadata.
     * This piggybacks on data already fetched in the PR query (flat fields on Commit type)
     * so it costs zero additional rate limit points.
     * <p>
     * R5: After upserting the commit, links it to the PR in the commit_pull_request join table
     * so that the association is established immediately (not deferred to enrichment).
     */
    private void upsertMergeCommit(GitHubPullRequestDTO dto, Repository repository) {
        var info = dto.mergeCommitInfo();
        if (info == null || info.sha() == null) {
            return;
        }

        Long authorId = commitAuthorResolver.resolveByLogin(info.authorLogin());
        Long committerId = commitAuthorResolver.resolveByLogin(info.committerLogin());

        String htmlUrl = "https://github.com/" + repository.getNameWithOwner() + "/commit/" + info.sha();

        // Defense-in-depth: git_commit.message is NOT NULL; default to empty string
        String message = info.message() != null ? info.message() : "";

        commitRepository.upsertCommit(
            info.sha(),
            message,
            info.messageBody(),
            htmlUrl,
            info.authoredDate(),
            info.committedDate(),
            info.additions(),
            info.deletions(),
            info.changedFiles(),
            Instant.now(),
            repository.getId(),
            authorId,
            committerId,
            info.authorEmail(),
            info.committerEmail()
        );

        // R5: Link the merge commit to the PR in the join table
        var commitOpt = commitRepository.findByShaAndRepositoryId(info.sha(), repository.getId());
        if (commitOpt.isPresent()) {
            commitRepository.linkCommitToPullRequests(
                commitOpt.get().getId(),
                repository.getId(),
                List.of(dto.number())
            );
        }

        log.debug("Upserted merge commit: sha={}, repository={}", info.sha(), repository.getNameWithOwner());
    }
}
