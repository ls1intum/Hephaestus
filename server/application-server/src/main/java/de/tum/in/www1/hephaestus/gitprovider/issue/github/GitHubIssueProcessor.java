package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueDTO;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.GitHubIssueTypeDTO;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified processor for GitHub issues.
 * <p>
 * This service handles the conversion of GitHubIssueDTO to Issue entities,
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
 * <p>
 * <b>Stub Issue Pattern:</b>
 * <p>
 * Some sync operations (dependency sync, sub-issue sync) need to create "stub"
 * issues for referential integrity - e.g., when a blocking issue exists in the
 * same org but hasn't been synced yet. Use {@link #processStub} for these cases.
 * Stub issues:
 * <ul>
 * <li>Are created with minimal data (may lack author, body, etc.)</li>
 * <li>Do NOT trigger domain events (they're not real "issue created" events)</li>
 * <li>Will be hydrated later by the full issue sync or webhook</li>
 * </ul>
 */
@Service
public class GitHubIssueProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueProcessor.class);

    private final IssueRepository issueRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubIssueProcessor(
        IssueRepository issueRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository,
        OrganizationRepository organizationRepository,
        GitHubIssueTypeSyncService issueTypeSyncService,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, labelRepository, milestoneRepository);
        this.issueRepository = issueRepository;
        this.organizationRepository = organizationRepository;
        this.issueTypeSyncService = issueTypeSyncService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a GitHub issue DTO and persist it as an Issue entity.
     * Publishes appropriate domain events based on what changed.
     * <p>
     * Use this method for real issue data from webhooks or full sync operations.
     * For creating placeholder issues (e.g., for dependency relationships),
     * use {@link #processStub} instead.
     */
    @Transactional
    public Issue process(GitHubIssueDTO dto, ProcessingContext context) {
        return processInternal(dto, context, true);
    }

    /**
     * Process a GitHub issue DTO as a "stub" entity for referential integrity.
     * <p>
     * <b>IMPORTANT:</b> This method does NOT publish domain events. Use it only
     * for creating placeholder issues needed for relationships (blocking issues,
     * parent issues, etc.) that will be hydrated later by the full sync.
     * <p>
     * Stub issues are created when:
     * <ul>
     * <li>Dependency sync finds a blocker issue not yet in the database</li>
     * <li>Sub-issue sync finds a parent issue not yet in the database</li>
     * <li>Comment webhooks arrive before the issue/PR webhook</li>
     * </ul>
     * <p>
     * These stubs have incomplete data (often missing author, body, etc.) and
     * should not trigger activity events. They will be "hydrated" with full data
     * when the real issue webhook or scheduled sync runs.
     *
     * @param dto     the issue DTO (may have incomplete data)
     * @param context the processing context
     * @return the created or updated Issue entity, or null if creation failed
     */
    @Transactional
    public Issue processStub(GitHubIssueDTO dto, ProcessingContext context) {
        return processInternal(dto, context, false);
    }

    /**
     * Internal method that handles both regular and stub issue processing.
     * <p>
     * Uses atomic upsert to prevent race conditions when concurrent threads
     * (e.g., multiple NATS consumers or webhook handlers) process the same issue.
     *
     * @param dto           the issue DTO
     * @param context       the processing context
     * @param publishEvents whether to publish domain events (false for stubs)
     * @return the created or updated Issue entity
     */
    private Issue processInternal(GitHubIssueDTO dto, ProcessingContext context, boolean publishEvents) {
        // Use getDatabaseId() which falls back to id for webhook payloads
        Long dbId = dto.getDatabaseId();
        if (dbId == null) {
            log.warn("Skipped issue processing: reason=missingDatabaseId, issueNumber={}", dto.number());
            return null;
        }

        Repository repository = context.repository();

        // Check if this is an update (for event publishing purposes)
        // We check by natural key (repository_id, number) to handle the case where
        // the same issue might have different IDs from different sources
        Optional<Issue> existingOpt = issueRepository.findByRepositoryIdAndNumber(repository.getId(), dto.number());
        boolean isNew = existingOpt.isEmpty();

        // Resolve related entities BEFORE the upsert
        User author = dto.author() != null ? findOrCreateUser(dto.author()) : null;
        Milestone milestone = dto.milestone() != null ? findOrCreateMilestone(dto.milestone(), repository) : null;
        IssueType issueType = null;
        if (dto.issueType() != null && repository.getOrganization() != null) {
            issueType = findOrCreateIssueType(dto.issueType(), repository.getOrganization().getLogin());
        }

        // Use atomic upsert to handle concurrent inserts
        // This uses ON CONFLICT (repository_id, number) DO UPDATE
        Instant now = Instant.now();
        issueRepository.upsertCore(
            dbId,
            dto.number(),
            sanitize(dto.title()),
            sanitize(dto.body()),
            convertState(dto.state()).name(),
            convertStateReason(dto.stateReason()) != null ? convertStateReason(dto.stateReason()).name() : null,
            dto.htmlUrl(),
            dto.locked(),
            dto.closedAt(),
            dto.commentsCount(), // int primitive, no null check needed
            now,
            dto.createdAt(),
            dto.updatedAt(),
            author != null ? author.getId() : null,
            repository.getId(),
            milestone != null ? milestone.getId() : null,
            issueType != null ? issueType.getId() : null,
            null, // parentIssueId - handled separately by dependency sync
            null, // subIssuesTotal - populated by sub-issue sync, not from DTO
            null, // subIssuesCompleted - populated by sub-issue sync, not from DTO
            null // subIssuesPercentCompleted - populated by sub-issue sync, not from DTO
        );

        // Fetch the issue to get a managed entity and handle relationships
        Issue issue = issueRepository
            .findByRepositoryIdAndNumber(repository.getId(), dto.number())
            .orElseThrow(() ->
                new IllegalStateException(
                    "Issue not found after upsert: repositoryId=" + repository.getId() + ", number=" + dto.number()
                )
            );

        // Handle ManyToMany relationships (labels, assignees) - these can't be done in the upsert
        boolean relationshipsChanged = updateRelationships(dto, issue, repository);

        // Save relationship changes
        if (relationshipsChanged) {
            issue = issueRepository.save(issue);
        }

        // Publish events
        if (publishEvents) {
            if (isNew) {
                eventPublisher.publishEvent(
                    new DomainEvent.IssueCreated(EventPayload.IssueData.from(issue), EventContext.from(context))
                );
                log.debug("Created issue: issueId={}, issueNumber={}", dbId, dto.number());
            } else {
                // For updates, we compute changed fields by comparing with what we know changed
                Set<String> changedFields = computeChangedFields(existingOpt.get(), issue);
                if (!changedFields.isEmpty() || relationshipsChanged) {
                    if (relationshipsChanged) {
                        changedFields.add("relationships");
                    }
                    eventPublisher.publishEvent(
                        new DomainEvent.IssueUpdated(
                            EventPayload.IssueData.from(issue),
                            changedFields,
                            EventContext.from(context)
                        )
                    );
                    log.debug("Updated issue: issueId={}, changedFields={}", dbId, changedFields);
                }
            }
        } else if (isNew) {
            log.debug("Created stub issue (no event): issueId={}, issueNumber={}", dbId, dto.number());
        }

        return issue;
    }

    /**
     * Updates ManyToMany relationships (labels, assignees) that can't be handled by the atomic upsert.
     *
     * @return true if any relationships were changed
     */
    private boolean updateRelationships(GitHubIssueDTO dto, Issue issue, Repository repository) {
        boolean changed = false;

        // Update assignees
        if (dto.assignees() != null) {
            Set<User> newAssignees = new HashSet<>();
            for (GitHubUserDTO assigneeDto : dto.assignees()) {
                User assignee = findOrCreateUser(assigneeDto);
                if (assignee != null) {
                    newAssignees.add(assignee);
                }
            }
            if (!issue.getAssignees().equals(newAssignees)) {
                issue.getAssignees().clear();
                issue.getAssignees().addAll(newAssignees);
                changed = true;
            }
        }

        // Update labels
        if (dto.labels() != null) {
            Set<Label> newLabels = new HashSet<>();
            for (GitHubLabelDTO labelDto : dto.labels()) {
                Label label = findOrCreateLabel(labelDto, repository);
                if (label != null) {
                    newLabels.add(label);
                }
            }
            if (!issue.getLabels().equals(newLabels)) {
                issue.getLabels().clear();
                issue.getLabels().addAll(newLabels);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Computes which fields changed between the old and new issue state.
     */
    private Set<String> computeChangedFields(Issue oldIssue, Issue newIssue) {
        Set<String> changedFields = new HashSet<>();

        if (!Objects.equals(oldIssue.getTitle(), newIssue.getTitle())) {
            changedFields.add("title");
        }
        if (!Objects.equals(oldIssue.getBody(), newIssue.getBody())) {
            changedFields.add("body");
        }
        if (oldIssue.getState() != newIssue.getState()) {
            changedFields.add("state");
        }
        if (!Objects.equals(oldIssue.getStateReason(), newIssue.getStateReason())) {
            changedFields.add("stateReason");
        }
        if (oldIssue.getCommentsCount() != newIssue.getCommentsCount()) {
            changedFields.add("commentsCount");
        }
        if (oldIssue.isLocked() != newIssue.isLocked()) {
            changedFields.add("locked");
        }
        if (!Objects.equals(oldIssue.getClosedAt(), newIssue.getClosedAt())) {
            changedFields.add("closedAt");
        }
        if (!Objects.equals(oldIssue.getMilestone(), newIssue.getMilestone())) {
            changedFields.add("milestone");
        }
        if (!Objects.equals(oldIssue.getIssueType(), newIssue.getIssueType())) {
            changedFields.add("issueType");
        }

        return changedFields;
    }

    /**
     * Process a typed event (issue type assigned).
     */
    @Transactional
    public Issue processTyped(
        GitHubIssueDTO issueDto,
        GitHubIssueTypeDTO typeDto,
        String orgLogin,
        ProcessingContext context
    ) {
        Issue issue = process(issueDto, context);

        if (typeDto != null) {
            IssueType issueType = findOrCreateIssueType(typeDto, orgLogin);
            if (issueType != null) {
                issue.setIssueType(issueType);
                issue = issueRepository.save(issue);
                eventPublisher.publishEvent(
                    new DomainEvent.IssueTyped(
                        EventPayload.IssueData.from(issue),
                        EventPayload.IssueTypeData.from(issueType),
                        EventContext.from(context)
                    )
                );
                log.debug("Updated issue type: issueId={}, issueTypeName={}", issue.getId(), issueType.getName());
            }
        }

        return issue;
    }

    /**
     * Process an untyped event (issue type removed).
     */
    @Transactional
    public Issue processUntyped(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);

        IssueType previousType = issue.getIssueType();
        if (previousType != null) {
            issue.setIssueType(null);
            issue = issueRepository.save(issue);
            eventPublisher.publishEvent(
                new DomainEvent.IssueUntyped(
                    EventPayload.IssueData.from(issue),
                    EventPayload.IssueTypeData.from(previousType),
                    EventContext.from(context)
                )
            );
            log.debug("Removed issue type: issueId={}, previousTypeName={}", issue.getId(), previousType.getName());
        }

        return issue;
    }

    /**
     * Process a closed event.
     */
    @Transactional
    public Issue processClosed(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        String stateReason = issueDto.stateReason() != null ? issueDto.stateReason() : "completed";
        eventPublisher.publishEvent(
            new DomainEvent.IssueClosed(EventPayload.IssueData.from(issue), stateReason, EventContext.from(context))
        );
        log.debug("Closed issue: issueId={}, stateReason={}", issue.getId(), stateReason);
        return issue;
    }

    /**
     * Process a reopened event.
     */
    @Transactional
    public Issue processReopened(GitHubIssueDTO issueDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        eventPublisher.publishEvent(
            new DomainEvent.IssueReopened(EventPayload.IssueData.from(issue), EventContext.from(context))
        );
        log.debug("Reopened issue: issueId={}", issue.getId());
        return issue;
    }

    /**
     * Process a labeled event.
     */
    @Transactional
    public Issue processLabeled(GitHubIssueDTO issueDto, GitHubLabelDTO labelDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueLabeled(
                    EventPayload.IssueData.from(issue),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            log.debug("Labeled issue: issueId={}, labelName={}", issue.getId(), label.getName());
        }
        return issue;
    }

    /**
     * Process an unlabeled event.
     */
    @Transactional
    public Issue processUnlabeled(GitHubIssueDTO issueDto, GitHubLabelDTO labelDto, ProcessingContext context) {
        Issue issue = process(issueDto, context);
        Label label = findOrCreateLabel(labelDto, context.repository());
        if (label != null) {
            eventPublisher.publishEvent(
                new DomainEvent.IssueUnlabeled(
                    EventPayload.IssueData.from(issue),
                    EventPayload.LabelData.from(label),
                    EventContext.from(context)
                )
            );
            log.debug("Unlabeled issue: issueId={}, labelName={}", issue.getId(), label.getName());
        }
        return issue;
    }

    /**
     * Process a deleted event.
     * Publishes IssueDeleted domain event.
     */
    @Transactional
    public void processDeleted(GitHubIssueDTO issueDto, ProcessingContext context) {
        Long dbId = issueDto.getDatabaseId();
        if (dbId != null) {
            issueRepository.deleteById(dbId);
            eventPublisher.publishEvent(new DomainEvent.IssueDeleted(dbId, EventContext.from(context)));
            log.info("Deleted issue: issueId={}", dbId);
        }
    }

    /**
     * Converts a GitHub API state string to Issue.State enum.
     * Note: Issues cannot be MERGED - only PRs can. This only handles OPEN/CLOSED.
     */
    private Issue.State convertState(String state) {
        if (state == null) {
            return Issue.State.OPEN;
        }
        return switch (state.toUpperCase()) {
            case "CLOSED" -> Issue.State.CLOSED;
            default -> Issue.State.OPEN;
        };
    }

    /**
     * Converts a GitHub API state reason string to Issue.StateReason enum.
     * Returns UNKNOWN for unrecognized values to preserve data integrity.
     */
    @Nullable
    private Issue.StateReason convertStateReason(String stateReason) {
        if (stateReason == null) {
            return null;
        }
        return switch (stateReason.toUpperCase()) {
            case "COMPLETED" -> Issue.StateReason.COMPLETED;
            case "DUPLICATE" -> Issue.StateReason.DUPLICATE;
            case "REOPENED" -> Issue.StateReason.REOPENED;
            case "NOT_PLANNED" -> Issue.StateReason.NOT_PLANNED;
            default -> Issue.StateReason.UNKNOWN;
        };
    }

    @Nullable
    private IssueType findOrCreateIssueType(GitHubIssueTypeDTO dto, String orgLogin) {
        if (dto == null || dto.nodeId() == null) {
            return null;
        }

        return issueTypeSyncService
            .findByNodeId(dto.nodeId())
            .orElseGet(() -> {
                var orgOpt = organizationRepository.findByLoginIgnoreCase(orgLogin);
                if (orgOpt.isEmpty()) {
                    log.warn(
                        "Skipped issue type creation: reason=orgNotFound, issueTypeName={}, orgLogin={}",
                        dto.name(),
                        orgLogin
                    );
                    return null;
                }
                return issueTypeSyncService.findOrCreateFromWebhook(
                    dto.nodeId(),
                    dto.name(),
                    dto.description(),
                    dto.color(),
                    dto.isEnabled() != null ? dto.isEnabled() : true,
                    orgOpt.get()
                );
            });
    }
}
